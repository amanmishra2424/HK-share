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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
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

    private final List<String> availableBatches = Arrays.asList(
        "Batch 1", "Batch 2", "Batch 3", "Batch 4", "Batch 5"
    );

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        // Get upload counts for each batch
        Map<String, Long> batchCounts = availableBatches.stream()
            .collect(Collectors.toMap(
                batch -> batch,
                batch -> (long) pdfUploadService.getBatchUploads(batch).size()
            ));

        model.addAttribute("batches", availableBatches);
        model.addAttribute("batchCounts", batchCounts);
        model.addAttribute("title", "Admin Dashboard - PDF Printing System");
        
        return "admin/dashboard";
    }

    @GetMapping("/batch/{batchName}")
    public String viewBatch(@PathVariable String batchName, Model model) {
        List<PdfUpload> uploads = pdfUploadService.getBatchUploads(batchName);
        
        model.addAttribute("batchName", batchName);
        model.addAttribute("uploads", uploads);
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
        // Get statistics for all batches
        Map<String, Long> batchCounts = availableBatches.stream()
            .collect(Collectors.toMap(
                batch -> batch,
                batch -> (long) pdfUploadService.getBatchUploads(batch).size()
            ));

        long totalPending = batchCounts.values().stream().mapToLong(Long::longValue).sum();
        
        model.addAttribute("batchCounts", batchCounts);
        model.addAttribute("totalPending", totalPending);
        model.addAttribute("title", "Statistics - Admin Dashboard");
        
        return "admin/statistics";
    }
}
