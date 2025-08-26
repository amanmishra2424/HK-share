package com.pdfprinting.repository;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PdfUploadRepository extends JpaRepository<PdfUpload, Long> {
    List<PdfUpload> findByUserOrderByUploadedAtDesc(User user);
    List<PdfUpload> findByBatchAndStatusOrderByUploadedAtAsc(String batch, PdfUpload.Status status);
    List<PdfUpload> findByBatchOrderByUploadedAtAsc(String batch);
    void deleteByBatchAndStatus(String batch, PdfUpload.Status status);
}
