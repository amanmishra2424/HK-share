package com.pdfprinting.controller;

import com.pdfprinting.service.PdfUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to test PDF page counting functionality
 * This can be used to verify that the PDF page counting works correctly
 */
@RestController
@RequestMapping("/api/test")
public class PdfTestController {

    @Autowired
    private PdfUploadService pdfUploadService;

    /**
     * Test endpoint to count pages in uploaded PDFs
     * POST /api/test/count-pages
     */
    @PostMapping("/count-pages")
    public ResponseEntity<Map<String, Object>> countPages(@RequestParam("files") MultipartFile[] files) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            int totalPages = pdfUploadService.calculateTotalPages(files);
            response.put("success", true);
            response.put("totalPages", totalPages);
            response.put("fileCount", files.length);
            
            // Calculate cost for 1 copy
            response.put("costFor1Copy", totalPages * 2);
            response.put("costFor2Copies", totalPages * 2 * 2);
            response.put("message", "PDF page counting successful!");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}