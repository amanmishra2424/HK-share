package com.pdfprinting.repository;

import com.pdfprinting.model.Transaction;
import com.pdfprinting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserOrderByCreatedAtDesc(User user);
    List<Transaction> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Transaction> findTop20ByUserOrderByCreatedAtDesc(User user);
    List<Transaction> findTop50ByOrderByCreatedAtDesc();
}