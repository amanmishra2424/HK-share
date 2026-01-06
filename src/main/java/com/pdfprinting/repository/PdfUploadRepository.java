package com.pdfprinting.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;

@Repository
public interface PdfUploadRepository extends JpaRepository<PdfUpload, Long> {
    List<PdfUpload> findByUserOrderByUploadedAtDesc(User user);
    
    // Only PENDING uploads for user - optimized query to avoid fetching processed records
    List<PdfUpload> findByUserAndStatusOrderByUploadedAtDesc(User user, PdfUpload.Status status);
    
    // Container-based queries (academicYear, branch, division, semester, batch)
    List<PdfUpload> findByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndStatusOrderByUploadedAtAsc(
        String academicYear, String branch, String division, String semester, String batch, PdfUpload.Status status);
    
    // Find by branch, division, and batch (legacy support)
    List<PdfUpload> findByBranchAndDivisionAndBatchAndStatusOrderByUploadedAtAsc(
        String branch, String division, String batch, PdfUpload.Status status);
    
    // Legacy methods
    List<PdfUpload> findByBatchAndStatusOrderByUploadedAtAsc(String batch, PdfUpload.Status status);
    List<PdfUpload> findByBatchOrderByUploadedAtAsc(String batch);
    
    // Delete methods - container-based
    void deleteByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndStatus(
        String academicYear, String branch, String division, String semester, String batch, PdfUpload.Status status);
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
    
    // Container-based count
    long countByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndStatus(
        String academicYear, String branch, String division, String semester, String batch, PdfUpload.Status status);
}
