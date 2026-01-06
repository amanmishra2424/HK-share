package com.pdfprinting.service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.repository.TransactionRepository;

@Service
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    public List<Transaction> getRecentTransactions(int limit) {
        // use repository method if exists, else stream limit
        try {
            return transactionRepository.findTop50ByOrderByCreatedAtDesc();
        } catch (Exception e) {
            return transactionRepository.findAll().stream()
                    .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }
    }

    public List<Transaction> getAllTransactionsDesc() {
        return transactionRepository.findAll().stream()
                .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Transaction> getUserTransactions(User user) {
        return transactionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Map<String, BigDecimal> getMonthlyTotalByType(Transaction.TransactionType type) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        return transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getType() == type)
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().format(fmt),
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)
                ));
    }

    public Map<String, Long> getMonthlyCounts() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        return transactionRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        t -> t.getCreatedAt().format(fmt),
                        Collectors.counting()
                ));
    }

    public Map<String, BigDecimal> getTotalsByType() {
        Map<String, BigDecimal> map = new HashMap<>();
        BigDecimal topups = sumByType(Transaction.TransactionType.WALLET_TOPUP);
        BigDecimal billing = sumByType(Transaction.TransactionType.PDF_BILLING); // This is negative in DB
        BigDecimal refunds = sumByType(Transaction.TransactionType.REFUND);
        
        // Billing is stored as negative, so abs value for display
        BigDecimal billingAbs = billing.abs();
        
        map.put("WALLET_TOPUP", topups);          // Credited amount
        map.put("PDF_BILLING", billing);           // Debited amount (negative)
        map.put("PDF_BILLING_ABS", billingAbs);    // Absolute value for display
        map.put("REFUND", refunds);                // Refunded amount (positive)
        // NET = topups - debits + refunds (remaining balance across all users)
        // Since billing is already negative, topups + billing + refunds = remaining
        map.put("NET", topups.add(billing).add(refunds));
        return map;
    }

    private BigDecimal sumByType(Transaction.TransactionType type) {
        return transactionRepository.findAll().stream()
                .filter(t -> t.getStatus() == Transaction.TransactionStatus.COMPLETED)
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
