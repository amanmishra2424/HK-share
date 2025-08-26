package com.pdfprinting.controller;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import com.pdfprinting.service.PdfUploadService;
import com.pdfprinting.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private UserService userService;

    @Autowired
    private PdfUploadService pdfUploadService;

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            return "redirect:/login";
        }

        List<PdfUpload> uploads = pdfUploadService.getUserUploads(user);
        
        model.addAttribute("user", user);
        model.addAttribute("uploads", uploads);
        model.addAttribute("title", "Student Dashboard - PDF Printing System");
        
        return "student/dashboard";
    }

    @PostMapping("/upload")
    public String uploadPdfs(@RequestParam("files") MultipartFile[] files,
                            @RequestParam("batch") String batch,
                            @RequestParam(value = "copyCount", defaultValue = "1") int copyCount,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/student/dashboard";
        }

        if (copyCount < 1 || copyCount > 50) {
            redirectAttributes.addFlashAttribute("error", "Copy count must be between 1 and 50");
            return "redirect:/student/dashboard";
        }

        try {
            int uploadedCount = pdfUploadService.uploadPdfs(files, batch, user, copyCount);
            redirectAttributes.addFlashAttribute("message", 
                uploadedCount + " PDF(s) uploaded successfully with " + copyCount + " copies each!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Upload failed: " + e.getMessage());
        }

        return "redirect:/student/dashboard";
    }

    @PostMapping("/delete/{id}")
    public String deletePdf(@PathVariable Long id,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/student/dashboard";
        }

        try {
            pdfUploadService.deletePdf(id, user);
            redirectAttributes.addFlashAttribute("message", "PDF deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Delete failed: " + e.getMessage());
        }

        return "redirect:/student/dashboard";
    }
}
