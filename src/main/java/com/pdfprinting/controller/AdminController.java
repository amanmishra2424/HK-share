package com.pdfprinting.controller;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.service.PdfMergeService;
import com.pdfprinting.service.PdfUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import com.pdfprinting.model.User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private PdfUploadService pdfUploadService;

    @Autowired
    private PdfMergeService pdfMergeService;

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

    @GetMapping("/statistics")
    public String statistics(Model model) {
        // Get statistics from pending uploads grouped by batch
        List<PdfUpload> pending = pdfUploadService.getPendingUploads();
        Map<String, Long> batchCounts = pending.stream()
            .collect(Collectors.groupingBy(PdfUpload::getBatch, Collectors.counting()));

        long totalPending = pending.size();

        model.addAttribute("batchCounts", batchCounts);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("title", "Statistics - Admin Dashboard");
        
        return "admin/statistics";
    }
}
