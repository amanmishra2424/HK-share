package com.pdfprinting.repository;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfUploadRepository extends JpaRepository<PdfUpload, Long> {
    List<PdfUpload> findByUserOrderByUploadedAtDesc(User user);
    
    // Find by branch, division, and batch
    List<PdfUpload> findByBranchAndDivisionAndBatchAndStatusOrderByUploadedAtAsc(
        String branch, String division, String batch, PdfUpload.Status status);
    
    // Legacy methods (updated to use branch and division)
    List<PdfUpload> findByBatchAndStatusOrderByUploadedAtAsc(String batch, PdfUpload.Status status);
    List<PdfUpload> findByBatchOrderByUploadedAtAsc(String batch);
    
    // Delete methods
    void deleteByBranchAndDivisionAndBatchAndStatus(
        String branch, String division, String batch, PdfUpload.Status status);
    void deleteByBatchAndStatus(String batch, PdfUpload.Status status);
    
    List<PdfUpload> findByStatus(PdfUpload.Status status);
    
    // New methods for reporting and analytics
    long countByUserIdAndBatch(Long userId, String batch);
    long countByUserId(Long userId);
    List<PdfUpload> findTop50ByOrderByUploadedAtDesc();
    List<PdfUpload> findByBatchOrderByUploadedAtDesc(String batch);
    List<PdfUpload> findByUserIdOrderByUploadedAtDesc(Long userId);
}
