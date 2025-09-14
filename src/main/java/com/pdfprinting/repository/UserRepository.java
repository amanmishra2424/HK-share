package com.pdfprinting.repository;

import com.pdfprinting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByVerificationToken(String token);
    boolean existsByEmail(String email);
    
    // Check if roll number exists in same branch and division
    boolean existsByBranchAndDivisionAndRollNumber(String branch, String division, String rollNumber);

    // Check if roll number exists anywhere (global) - useful to detect leftover DB constraints
    boolean existsByRollNumber(String rollNumber);
    
    // Find users by branch, division, and optionally batch
    List<User> findByBranchAndDivision(String branch, String division);
    List<User> findByBranchAndDivisionAndBatch(String branch, String division, String batch);
}
