package com.pdfprinting.service;

import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import com.pdfprinting.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        BigDecimal billing = sumByType(Transaction.TransactionType.PDF_BILLING);
        BigDecimal refunds = sumByType(Transaction.TransactionType.REFUND);
        map.put("WALLET_TOPUP", topups);
        map.put("PDF_BILLING", billing);
        map.put("REFUND", refunds);
        map.put("NET", topups.add(refunds).subtract(billing));
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
