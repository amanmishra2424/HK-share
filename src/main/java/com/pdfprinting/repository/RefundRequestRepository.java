package com.pdfprinting.repository;

import com.pdfprinting.model.RefundRequest;
import com.pdfprinting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    List<RefundRequest> findByStatusOrderByCreatedAtDesc(RefundRequest.Status status);
    List<RefundRequest> findByUserOrderByCreatedAtDesc(User user);
    boolean existsByUserAndStatus(User user, RefundRequest.Status status);
}
