package com.pdfprinting.controller;

import com.pdfprinting.model.Transaction;
import com.pdfprinting.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/transactions")
public class AdminTransactionController {

    @Autowired
    private TransactionService transactionService;

    @GetMapping
    public String viewTransactions(Model model) {
        Map<String, BigDecimal> totalsByType = transactionService.getTotalsByType();
        Map<String, Long> monthlyCounts = transactionService.getMonthlyCounts();
        Map<String, BigDecimal> monthlyTopups = transactionService.getMonthlyTotalByType(Transaction.TransactionType.WALLET_TOPUP);
        Map<String, BigDecimal> monthlyBilling = transactionService.getMonthlyTotalByType(Transaction.TransactionType.PDF_BILLING);
        Map<String, BigDecimal> monthlyRefunds = transactionService.getMonthlyTotalByType(Transaction.TransactionType.REFUND);
        List<Transaction> recent = transactionService.getRecentTransactions(50);

        model.addAttribute("totals", totalsByType);
        model.addAttribute("monthlyCounts", monthlyCounts);
        model.addAttribute("monthlyTopups", monthlyTopups);
        model.addAttribute("monthlyBilling", monthlyBilling);
        model.addAttribute("monthlyRefunds", monthlyRefunds);
        model.addAttribute("recentTransactions", recent);
        model.addAttribute("title", "Transaction Reports - Admin");
        return "admin/transaction-reports";
    }
}
