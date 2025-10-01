package com.pdfprinting.controller;

import com.pdfprinting.model.RefundRequest;
import com.pdfprinting.service.RefundService;
import com.pdfprinting.service.WalletService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/refunds")
public class AdminRefundController {

    @Autowired
    private RefundService refundService;

    @Autowired
    private WalletService walletService;

    @GetMapping
    public String list(Model model) {
        List<RefundRequest> pending = refundService.listPending();
        // Build a map of userId -> wallet balance for quick access in the view
        Map<Long, BigDecimal> walletBalances = new HashMap<>();
        for (RefundRequest rr : pending) {
            Long userId = rr.getUser().getId();
            if (!walletBalances.containsKey(userId)) {
                walletBalances.put(userId, walletService.getWalletBalance(rr.getUser()));
            }
        }
        model.addAttribute("pendingRefunds", pending);
        model.addAttribute("walletBalances", walletBalances);
        model.addAttribute("title", "Refund Requests - Admin");
        return "admin/refunds";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam("payoutReference") String payoutReference,
                          @RequestParam(value = "adminNote", required = false) String adminNote,
                          RedirectAttributes redirectAttributes) {
        try {
            refundService.approveAndProcess(id, payoutReference, adminNote);
            redirectAttributes.addFlashAttribute("message", "Refund processed and wallet deducted.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/refunds";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam(value = "adminNote", required = false) String adminNote,
                         RedirectAttributes redirectAttributes) {
        try {
            refundService.reject(id, adminNote);
            redirectAttributes.addFlashAttribute("message", "Refund request rejected.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/refunds";
    }
}
