package com.pdfprinting.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.RefundRequest;
import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.service.PdfUploadService;
import com.pdfprinting.service.RefundService;
import com.pdfprinting.service.UserService;
import com.pdfprinting.service.WalletService;

@Controller
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private UserService userService;

    @Autowired
    private PdfUploadService pdfUploadService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private RefundService refundService;

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            return "redirect:/login";
        }

        List<PdfUpload> uploads = pdfUploadService.getUserUploads(user);
        BigDecimal walletBalance = walletService.getWalletBalance(user);
        List<Transaction> recentTransactions = walletService.getRecentTransactions(user, 10);
        boolean hasPendingRefund = refundService.listByUser(user).stream()
            .anyMatch(r -> r.getStatus() == RefundRequest.Status.PENDING);
        
        model.addAttribute("user", user);
        model.addAttribute("uploads", uploads);
        model.addAttribute("walletBalance", walletBalance);
        model.addAttribute("recentTransactions", recentTransactions);
        model.addAttribute("hasPendingRefund", hasPendingRefund);
        model.addAttribute("title", "Student Dashboard - PDF Printing System");
        
        return "student/dashboard";
    }

    @PostMapping("/refund/request")
    public String requestRefund(@RequestParam("amount") BigDecimal amount,
                                @RequestParam("upiId") String upiId,
                                @RequestParam(value = "reason", required = false) String reason,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/student/dashboard";
        }
        try {
            refundService.createRequest(user, amount, upiId, reason);
            redirectAttributes.addFlashAttribute("message", "Refund request submitted. A 2% processing fee applies, and processing may take up to 7 working days.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/student/dashboard";
    }

    @PostMapping("/upload")
    public String uploadPdfs(@RequestParam("files") MultipartFile[] files,
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

        // Check if user has academicYear set
        if (user.getAcademicYear() == null || user.getAcademicYear().isBlank()) {
            redirectAttributes.addFlashAttribute("error", 
                "Your academic year is not set. Please contact support or register again to set your academic year before uploading PDFs.");
            return "redirect:/student/dashboard";
        }

        try {
            // Calculate total cost first
            int totalPages = pdfUploadService.calculateTotalPages(files);
            BigDecimal totalCost = walletService.calculateCost(totalPages, copyCount);
            
            // Check wallet balance
            if (!walletService.hasAmountRequired(user, totalCost)) {
                redirectAttributes.addFlashAttribute("error", 
                    "Insufficient wallet balance. Required: ₹" + totalCost + ", Available: ₹" + 
                    walletService.getWalletBalance(user));
                return "redirect:/student/dashboard";
            }
            
            // Process upload and deduct amount
            // Always use the student's registered batch (auto-assigned) rather than a user-provided value
            String batch = user.getBatch();
            int uploadedCount = pdfUploadService.uploadPdfs(files, batch, user, copyCount);
            walletService.deductMoney(user, totalCost, "PDF printing cost for " + uploadedCount + " files");
            
            redirectAttributes.addFlashAttribute("message", 
                uploadedCount + " PDF(s) uploaded successfully with " + copyCount + " copies each! ₹" + totalCost + " deducted from wallet.");
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
            // Get PDF details before deletion for refund
            PdfUpload pdf = pdfUploadService.getPdfById(id);
            if (pdf != null && pdf.getUser().equals(user) && 
                pdf.getStatus() == PdfUpload.Status.PENDING) {
                
                // Refund money to wallet
                BigDecimal refundAmount = pdf.getTotalCost();
                walletService.refundMoney(user, refundAmount, "Refund for deleted PDF: " + pdf.getOriginalFileName());
                
                pdfUploadService.deletePdf(id, user);
                redirectAttributes.addFlashAttribute("message", 
                    "PDF deleted successfully! ₹" + refundAmount + " refunded to your wallet.");
            } else {
                pdfUploadService.deletePdf(id, user);
                redirectAttributes.addFlashAttribute("message", "PDF deleted successfully!");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Delete failed: " + e.getMessage());
        }

        return "redirect:/student/dashboard";
    }

    @GetMapping("/wallet")
    public String walletPage(Authentication authentication, Model model) {
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            return "redirect:/login";
        }

        BigDecimal walletBalance = walletService.getWalletBalance(user);
        List<Transaction> transactions = walletService.getAllTransactions(user);
        List<RefundRequest> refundRequests = refundService.listByUser(user);
        
        model.addAttribute("user", user);
        model.addAttribute("walletBalance", walletBalance);
        model.addAttribute("transactions", transactions);
        model.addAttribute("refundRequests", refundRequests);
        model.addAttribute("title", "My Wallet - PDF Printing System");
        
        return "student/wallet";
    }
}
