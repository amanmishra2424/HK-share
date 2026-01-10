package com.pdfprinting.controller;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.PdfUpload.PrintType;
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

    /**
     * Simple container info class for flat card display
     * Now includes counts per print type
     */
    public static class ContainerInfo {
        private String academicYear;
        private String branch;
        private String division;
        private String semester;
        private String batch;
        private long fileCount;
        private long singleSideCount;
        private long doubleSideCount;
        private long colourCount;
        
        public ContainerInfo(String academicYear, String branch, String division, 
                            String semester, String batch, long fileCount,
                            long singleSideCount, long doubleSideCount, long colourCount) {
            this.academicYear = academicYear;
            this.branch = branch;
            this.division = division;
            this.semester = semester;
            this.batch = batch;
            this.fileCount = fileCount;
            this.singleSideCount = singleSideCount;
            this.doubleSideCount = doubleSideCount;
            this.colourCount = colourCount;
        }
        
        // Getters
        public String getAcademicYear() { return academicYear; }
        public String getBranch() { return branch; }
        public String getDivision() { return division; }
        public String getSemester() { return semester; }
        public String getBatch() { return batch; }
        public long getFileCount() { return fileCount; }
        public long getSingleSideCount() { return singleSideCount; }
        public long getDoubleSideCount() { return doubleSideCount; }
        public long getColourCount() { return colourCount; }
        
        // Display label for card
        public String getDisplayLabel() {
            return academicYear + " | " + branch + " | Div " + division + " | Sem " + semester + " | " + batch;
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Build flat list of containers with counts for simple card display
        List<PdfUpload> pending = pdfUploadService.getPendingUploads();

        // Group by container key and count files, also track print type counts
        Map<String, List<PdfUpload>> containerGroups = pending.stream()
            .collect(Collectors.groupingBy(
                upload -> String.join("|",
                    safe(upload.getAcademicYear()),
                    safe(upload.getBranch()),
                    safe(upload.getDivision()),
                    safe(upload.getSemester()),
                    safe(upload.getBatch())
                )
            ));
        
        // Convert to flat list of ContainerInfo with print type counts
        List<ContainerInfo> containers = new ArrayList<>();
        for (Map.Entry<String, List<PdfUpload>> entry : containerGroups.entrySet()) {
            String[] parts = entry.getKey().split("\\|", -1);
            if (parts.length == 5) {
                List<PdfUpload> uploads = entry.getValue();
                
                // Count by print type
                long singleSideCount = uploads.stream()
                    .filter(u -> u.getPrintType() == PrintType.SINGLE_SIDE)
                    .count();
                long doubleSideCount = uploads.stream()
                    .filter(u -> u.getPrintType() == PrintType.DOUBLE_SIDE)
                    .count();
                long colourCount = uploads.stream()
                    .filter(u -> u.getPrintType() == PrintType.COLOUR)
                    .count();
                
                containers.add(new ContainerInfo(
                    parts[0].isEmpty() ? "Unknown Year" : parts[0],
                    parts[1].isEmpty() ? "Unknown Branch" : parts[1],
                    parts[2].isEmpty() ? "Unknown Division" : parts[2],
                    parts[3].isEmpty() ? "Unknown Semester" : parts[3],
                    parts[4].isEmpty() ? "Unknown Batch" : parts[4],
                    uploads.size(),
                    singleSideCount,
                    doubleSideCount,
                    colourCount
                ));
            }
        }
        
        // Sort by year, branch, division, semester, batch
        containers.sort((a, b) -> {
            int c = a.getAcademicYear().compareTo(b.getAcademicYear());
            if (c != 0) return c;
            c = a.getBranch().compareTo(b.getBranch());
            if (c != 0) return c;
            c = a.getDivision().compareTo(b.getDivision());
            if (c != 0) return c;
            c = a.getSemester().compareTo(b.getSemester());
            if (c != 0) return c;
            return a.getBatch().compareTo(b.getBatch());
        });

        long totalPending = pending.size();

        model.addAttribute("containers", containers);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("title", "Admin Dashboard - Print For You");
        
        return "admin/dashboard";
    }
    
    private String safe(String v) { return v == null ? "" : v; }

    /**
     * View container details using container key format: year|branch|division|semester|batch
     */
    @GetMapping("/container")
    public String viewContainer(
            @RequestParam String academicYear,
            @RequestParam String branch,
            @RequestParam String division,
            @RequestParam String semester,
            @RequestParam String batch,
            Model model) {
        
        List<PdfUpload> uploads = pdfUploadService.getContainerUploads(
            academicYear, branch, division, semester, batch);
        
        long totalFiles = uploads.size();
        double totalSizeMb = uploads.stream().mapToLong(PdfUpload::getFileSize).sum() / 1024.0 / 1024.0;
        long uniqueStudents = uploads.stream().map(u -> u.getUser().getId()).distinct().count();
        int totalCopies = uploads.stream().mapToInt(PdfUpload::getCopyCount).sum();
        
        // Print type breakdown counts
        long singleSideCount = uploads.stream()
            .filter(u -> u.getPrintType() == PdfUpload.PrintType.SINGLE_SIDE)
            .count();
        long doubleSideCount = uploads.stream()
            .filter(u -> u.getPrintType() == PdfUpload.PrintType.DOUBLE_SIDE)
            .count();
        long colourCount = uploads.stream()
            .filter(u -> u.getPrintType() == PdfUpload.PrintType.COLOUR)
            .count();

        // Container info for display
        model.addAttribute("academicYear", academicYear);
        model.addAttribute("branch", branch);
        model.addAttribute("division", division);
        model.addAttribute("semester", semester);
        model.addAttribute("batch", batch);
        model.addAttribute("containerKey", PdfMergeService.getContainerKey(academicYear, branch, division, semester, batch));
        
        model.addAttribute("uploads", uploads);
        model.addAttribute("totalFiles", totalFiles);
        model.addAttribute("totalSizeMb", totalSizeMb);
        model.addAttribute("uniqueStudents", uniqueStudents);
        model.addAttribute("totalCopies", totalCopies);
        model.addAttribute("singleSideCount", singleSideCount);
        model.addAttribute("doubleSideCount", doubleSideCount);
        model.addAttribute("colourCount", colourCount);
        model.addAttribute("title", batch + " - Container Details");
        
        return "admin/batch-details";
    }

    /**
     * Legacy batch view - kept for backward compatibility
     * @deprecated Use viewContainer instead
     */
    @Deprecated
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

    /**
     * Merge all PDFs in a container
     */
    @PostMapping("/merge")
    public String mergeContainer(
            @RequestParam String academicYear,
            @RequestParam String branch,
            @RequestParam String division,
            @RequestParam String semester,
            @RequestParam String batch,
            RedirectAttributes redirectAttributes) {
        try {
            List<PdfUpload> uploads = pdfUploadService.getContainerUploads(
                academicYear, branch, division, semester, batch);
            
            if (uploads.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "No pending uploads found for container");
                return "redirect:/admin/dashboard";
            }

            // Merge PDFs from container with failure reporting
            PdfMergeService.MergeResult result = pdfMergeService.mergeContainerPdfsWithReport(
                academicYear, branch, division, semester, batch);
            System.out.println("Merged PDF size: " + (result.getMergedPdf() == null ? 0 : result.getMergedPdf().length));
            
            // Mark all uploads in container as PROCESSED
            pdfUploadService.clearContainerUploads(academicYear, branch, division, semester, batch);
            
            String containerKey = PdfMergeService.getContainerKey(academicYear, branch, division, semester, batch);
            
            // Build success message including failure info
            String message;
            if (result.hasFailures()) {
                message = result.getSuccessCount() + " of " + result.getTotalCount() + 
                    " PDFs merged. " + result.getFailedCount() + " PDFs failed (see below).";
                redirectAttributes.addFlashAttribute("failedPdfs", result.getFailedPdfs());
                redirectAttributes.addFlashAttribute("hasFailures", true);
            } else {
                message = result.getSuccessCount() + " PDFs from container have been merged successfully!";
            }
            
            redirectAttributes.addFlashAttribute("message", message);
            redirectAttributes.addFlashAttribute("downloadReady", true);
            redirectAttributes.addFlashAttribute("containerKey", containerKey);
            redirectAttributes.addFlashAttribute("academicYear", academicYear);
            redirectAttributes.addFlashAttribute("branch", branch);
            redirectAttributes.addFlashAttribute("division", division);
            redirectAttributes.addFlashAttribute("semester", semester);
            redirectAttributes.addFlashAttribute("batch", batch);
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Failed to merge PDFs: " + e.getMessage());
        }
        
        return "redirect:/admin/dashboard";
    }
    
    /**
     * Merge PDFs in a container by print type
     */
    @PostMapping("/merge-by-type")
    public String mergeContainerByPrintType(
            @RequestParam String academicYear,
            @RequestParam String branch,
            @RequestParam String division,
            @RequestParam String semester,
            @RequestParam String batch,
            @RequestParam String printType,
            RedirectAttributes redirectAttributes) {
        try {
            PrintType type = PrintType.valueOf(printType.toUpperCase());
            
            List<PdfUpload> uploads = pdfUploadService.getContainerUploadsByPrintType(
                academicYear, branch, division, semester, batch, type);
            
            if (uploads.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", 
                    "No " + type.getDisplayName() + " uploads found for container");
                return "redirect:/admin/dashboard";
            }

            // Merge PDFs from container by print type with failure reporting
            PdfMergeService.MergeResult result = pdfMergeService.mergeContainerPdfsByPrintTypeWithReport(
                academicYear, branch, division, semester, batch, type);
            System.out.println("Merged " + type.getDisplayName() + " PDF size: " + (result.getMergedPdf() == null ? 0 : result.getMergedPdf().length));
            
            // Mark uploads of this print type in container as PROCESSED
            pdfUploadService.clearContainerUploadsByPrintType(academicYear, branch, division, semester, batch, type);
            
            // Build success message including failure info
            String message;
            if (result.hasFailures()) {
                message = result.getSuccessCount() + " of " + result.getTotalCount() + " " + 
                    type.getDisplayName() + " PDFs merged. " + result.getFailedCount() + " PDFs failed (see below).";
                redirectAttributes.addFlashAttribute("failedPdfs", result.getFailedPdfs());
                redirectAttributes.addFlashAttribute("hasFailures", true);
            } else {
                message = result.getSuccessCount() + " " + type.getDisplayName() + " PDFs have been merged successfully!";
            }
            
            redirectAttributes.addFlashAttribute("message", message);
            redirectAttributes.addFlashAttribute("downloadReady", true);
            redirectAttributes.addFlashAttribute("downloadPrintType", printType);
            redirectAttributes.addFlashAttribute("academicYear", academicYear);
            redirectAttributes.addFlashAttribute("branch", branch);
            redirectAttributes.addFlashAttribute("division", division);
            redirectAttributes.addFlashAttribute("semester", semester);
            redirectAttributes.addFlashAttribute("batch", batch);
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid print type: " + printType);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Failed to merge PDFs: " + e.getMessage());
        }
        
        return "redirect:/admin/dashboard";
    }

    /**
     * Legacy merge by batch name
     * @deprecated Use mergeContainer instead
     */
    @Deprecated
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

            byte[] mergedPdf = pdfMergeService.mergeBatchPdfs(batchName);
            System.out.println("Merged PDF size: " + (mergedPdf == null ? 0 : mergedPdf.length));
            
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

    /**
     * Download merged PDF for a container
     */
    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadContainerPdf(
            @RequestParam String academicYear,
            @RequestParam String branch,
            @RequestParam String division,
            @RequestParam String semester,
            @RequestParam String batch) {
        try {
            byte[] mergedPdf = pdfMergeService.getMergedPdfByContainer(
                academicYear, branch, division, semester, batch);
            
            ByteArrayResource resource = new ByteArrayResource(mergedPdf);
            
            String filename = String.join("_", academicYear, branch, division, semester, batch)
                .replaceAll("[^a-zA-Z0-9_-]", "_") + "_merged.pdf";
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(mergedPdf.length)
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Download merged PDF for a container by print type
     */
    @GetMapping("/download-by-type")
    public ResponseEntity<ByteArrayResource> downloadContainerPdfByPrintType(
            @RequestParam String academicYear,
            @RequestParam String branch,
            @RequestParam String division,
            @RequestParam String semester,
            @RequestParam String batch,
            @RequestParam String printType) {
        try {
            PrintType type = PrintType.valueOf(printType.toUpperCase());
            
            byte[] mergedPdf = pdfMergeService.getMergedPdfByContainerAndPrintType(
                academicYear, branch, division, semester, batch, type);
            
            ByteArrayResource resource = new ByteArrayResource(mergedPdf);
            
            String typeLabel = type.name().toLowerCase().replace("_", "-");
            String filename = String.join("_", academicYear, branch, division, semester, batch, typeLabel)
                .replaceAll("[^a-zA-Z0-9_-]", "_") + "_merged.pdf";
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(mergedPdf.length)
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Download a single PDF by upload ID (for downloading failed/problematic PDFs)
     */
    @GetMapping("/download-single/{uploadId}")
    public ResponseEntity<ByteArrayResource> downloadSinglePdf(@PathVariable Long uploadId) {
        try {
            PdfUpload upload = pdfUploadService.getPdfById(uploadId);
            if (upload == null) {
                return ResponseEntity.notFound().build();
            }
            
            byte[] pdfBytes = pdfMergeService.downloadPdfById(uploadId);
            ByteArrayResource resource = new ByteArrayResource(pdfBytes);
            
            String filename = upload.getOriginalFileName();
            if (!filename.toLowerCase().endsWith(".pdf")) {
                filename += ".pdf";
            }
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(resource);
                
        } catch (Exception e) {
            System.err.println("Failed to download single PDF: " + e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Legacy download by batch name
     * @deprecated Use downloadContainerPdf instead
     */
    @Deprecated
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
