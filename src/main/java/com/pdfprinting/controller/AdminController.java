package com.pdfprinting.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import com.pdfprinting.service.PdfMergeService;
import com.pdfprinting.service.PdfUploadService;
import com.pdfprinting.service.UserService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private PdfUploadService pdfUploadService;

    @Autowired
    private PdfMergeService pdfMergeService;

    @Autowired
    private UserService userService;

    // Departments mapping is derived from pending uploads at runtime.

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Build hierarchy from pending uploads: department -> division -> batch -> count
        List<PdfUpload> pending = pdfUploadService.getPendingUploads();

        Map<String, Map<String, Map<String, Long>>> hierarchy = new java.util.LinkedHashMap<>();

        for (PdfUpload upload : pending) {
            User user = upload.getUser();
            if (user == null) continue;
            String dept = user.getBranch() == null ? "Unknown Department" : user.getBranch();
            String div = user.getDivision() == null ? "Unknown Division" : user.getDivision();
            String batch = upload.getBatch() == null ? "Unknown Batch" : upload.getBatch();

            hierarchy.computeIfAbsent(dept, d -> new java.util.LinkedHashMap<>())
                     .computeIfAbsent(div, d -> new java.util.LinkedHashMap<>())
                     .merge(batch, 1L, Long::sum);
        }

        long totalPending = pending.size();

        model.addAttribute("hierarchy", hierarchy);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("title", "Admin Dashboard - PDF Printing System");
        
        return "admin/dashboard";
    }

    @GetMapping("/batch/{batchName}")
    public String viewBatch(@PathVariable String batchName, Model model) {
        List<PdfUpload> uploads = pdfUploadService.getBatchUploads(batchName);
        
    long totalFiles = uploads.size();
    double totalSizeMb = uploads.stream().mapToLong(PdfUpload::getFileSize).sum() / 1024.0 / 1024.0;
    long uniqueStudents = uploads.stream().map(u -> u.getUser().getId()).distinct().count();

    model.addAttribute("batchName", batchName);
    model.addAttribute("uploads", uploads);
    model.addAttribute("totalFiles", totalFiles);
    model.addAttribute("totalSizeMb", totalSizeMb);
    model.addAttribute("uniqueStudents", uniqueStudents);
        model.addAttribute("title", batchName + " - Admin Dashboard");
        
        return "admin/batch-details";
    }

    @PostMapping("/merge/{batchName}")
    public String mergeBatch(@PathVariable String batchName, 
                            RedirectAttributes redirectAttributes) {
        try {
            List<PdfUpload> uploads = pdfUploadService.getBatchUploads(batchName);
            
            if (uploads.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "No pending uploads found for " + batchName);
                return "redirect:/admin/dashboard";
            }

            // Store merged PDF in session or temporary storage for download
            byte[] mergedPdf = pdfMergeService.mergeBatchPdfs(batchName);
            // use mergedPdf length in a debug log to avoid unused variable warning
            System.out.println("Merged PDF size: " + (mergedPdf == null ? 0 : mergedPdf.length));
            
            // Clear the batch queue
            pdfUploadService.clearBatchUploads(batchName);
            
            redirectAttributes.addFlashAttribute("message", 
                uploads.size() + " PDFs from " + batchName + " have been merged successfully!");
            redirectAttributes.addFlashAttribute("downloadReady", true);
            redirectAttributes.addFlashAttribute("batchName", batchName);
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Failed to merge PDFs: " + e.getMessage());
        }
        
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/download/{batchName}")
    public ResponseEntity<ByteArrayResource> downloadMergedPdf(@PathVariable String batchName) {
        try {
            byte[] mergedPdf = pdfMergeService.getMergedPdf(batchName);
            
            ByteArrayResource resource = new ByteArrayResource(mergedPdf);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, 
                    "attachment; filename=\"" + batchName.replace(" ", "_") + "_merged.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(mergedPdf.length)
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/students/{batchName}")
    public String viewStudents(@PathVariable String batchName, Model model) {
        // Get all students in the specified batch
        List<User> students = userService.getStudentsByBatch(batchName);
        
        // Get upload statistics for each student in this batch
        Map<Long, Long> studentUploadCounts = students.stream()
            .collect(Collectors.toMap(
                User::getId,
                student -> pdfUploadService.getUploadCountByUserAndBatch(student.getId(), batchName)
            ));
        
        model.addAttribute("batchName", batchName);
        model.addAttribute("students", students);
        model.addAttribute("studentUploadCounts", studentUploadCounts);
        model.addAttribute("title", "Students in " + batchName + " - Admin Dashboard");
        
        return "admin/students";
    }

    @GetMapping("/all-students")
    public String viewAllStudents(Model model) {
        // Get all registered students
        List<User> allStudents = userService.getAllStudents();
        
        // Group students by batch
        Map<String, List<User>> studentsByBatch = allStudents.stream()
            .collect(Collectors.groupingBy(User::getBatch));
        
        // Get upload count for each student
        Map<Long, Long> studentUploadCounts = allStudents.stream()
            .collect(Collectors.toMap(
                User::getId,
                student -> pdfUploadService.getUploadCountByUser(student.getId())
            ));
        
        // Calculate summary statistics
        long totalStudents = allStudents.size();
        long verifiedStudents = allStudents.stream()
            .mapToLong(student -> student.isEmailVerified() ? 1L : 0L)
            .sum();
        long unverifiedStudents = totalStudents - verifiedStudents;
        long totalUploads = studentUploadCounts.values().stream()
            .mapToLong(count -> count)
            .sum();
        
        model.addAttribute("allStudents", allStudents);
        model.addAttribute("studentsByBatch", studentsByBatch);
        model.addAttribute("studentUploadCounts", studentUploadCounts);
        model.addAttribute("totalStudents", totalStudents);
        model.addAttribute("verifiedStudents", verifiedStudents);
        model.addAttribute("unverifiedStudents", unverifiedStudents);
        model.addAttribute("totalUploads", totalUploads);
        model.addAttribute("title", "All Registered Students - Admin Dashboard");
        
        return "admin/all-students";
    }

    @GetMapping("/student/{studentId}")
    public String viewStudentDetails(@PathVariable Long studentId, Model model) {
        // Get student details
        User student = userService.getStudentById(studentId);
        if (student == null) {
            model.addAttribute("error", "Student not found");
            return "redirect:/admin/dashboard";
        }
        
        // Get all uploads by this student
        List<PdfUpload> studentUploads = pdfUploadService.getAllUploadsByStudent(studentId);
        
        // Calculate statistics
        long totalUploads = studentUploads.size();
        long pendingUploads = studentUploads.stream()
            .mapToLong(upload -> upload.getStatus() == PdfUpload.Status.PENDING ? 1L : 0L)
            .sum();
        long processedUploads = totalUploads - pendingUploads;
        double totalSizeMb = studentUploads.stream()
            .mapToLong(PdfUpload::getFileSize)
            .sum() / 1024.0 / 1024.0;
        
        // Group uploads by batch
        Map<String, List<PdfUpload>> uploadsByBatch = studentUploads.stream()
            .collect(Collectors.groupingBy(PdfUpload::getBatch));
        
        // Group uploads by month
        Map<String, Long> monthlyUploads = studentUploads.stream()
            .collect(Collectors.groupingBy(
                upload -> upload.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));
        
        model.addAttribute("student", student);
        model.addAttribute("studentUploads", studentUploads);
        model.addAttribute("totalUploads", totalUploads);
        model.addAttribute("pendingUploads", pendingUploads);
        model.addAttribute("processedUploads", processedUploads);
        model.addAttribute("totalSizeMb", totalSizeMb);
        model.addAttribute("uploadsByBatch", uploadsByBatch);
        model.addAttribute("monthlyUploads", monthlyUploads);
        model.addAttribute("title", student.getName() + " - Student Details");
        
        return "admin/student-details";
    }

    @GetMapping("/reports")
    public String viewReports(Model model) {
        // Get monthly upload statistics
        Map<String, Long> monthlyUploads = pdfUploadService.getMonthlyUploadStats();
        
        // Get batch-wise statistics
        Map<String, Map<String, Long>> batchStats = pdfUploadService.getBatchStatistics();
        
        // Get recent uploads (last 50)
        List<PdfUpload> recentUploads = pdfUploadService.getRecentUploads(50);
        
        model.addAttribute("monthlyUploads", monthlyUploads);
        model.addAttribute("batchStats", batchStats);
        model.addAttribute("recentUploads", recentUploads);
        model.addAttribute("title", "Reports & Analytics - Admin Dashboard");
        
        return "admin/reports";
    }

    @GetMapping("/reports/{batchName}")
    public String viewBatchReport(@PathVariable String batchName, Model model) {
        // Get detailed batch report
        List<PdfUpload> batchUploads = pdfUploadService.getAllBatchUploads(batchName);
        
        // Calculate statistics
        long totalFiles = batchUploads.size();
        long processedFiles = batchUploads.stream()
            .mapToLong(upload -> upload.getStatus() == PdfUpload.Status.PROCESSED ? 1L : 0L)
            .sum();
        long pendingFiles = totalFiles - processedFiles;
        double totalSizeMb = batchUploads.stream()
            .mapToLong(PdfUpload::getFileSize)
            .sum() / 1024.0 / 1024.0;
        
        // Group by month
        Map<String, Long> monthlyStats = batchUploads.stream()
            .collect(Collectors.groupingBy(
                upload -> upload.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));
        
        model.addAttribute("batchName", batchName);
        model.addAttribute("batchUploads", batchUploads);
        model.addAttribute("totalFiles", totalFiles);
        model.addAttribute("processedFiles", processedFiles);
        model.addAttribute("pendingFiles", pendingFiles);
        model.addAttribute("totalSizeMb", totalSizeMb);
        model.addAttribute("monthlyStats", monthlyStats);
        model.addAttribute("title", batchName + " Report - Admin Dashboard");
        
        return "admin/batch-report";
    }


}
