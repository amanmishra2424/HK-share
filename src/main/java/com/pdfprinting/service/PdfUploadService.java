package com.pdfprinting.service;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import com.pdfprinting.repository.PdfUploadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PdfUploadService {

    @Autowired
    private PdfUploadRepository pdfUploadRepository;

    @Autowired
    private GitHubStorageService gitHubStorageService;

    public List<PdfUpload> getUserUploads(User user) {
        return pdfUploadRepository.findByUserOrderByUploadedAtDesc(user);
    }

    public List<PdfUpload> getBatchUploads(String batch) {
        return pdfUploadRepository.findByBatchAndStatusOrderByUploadedAtAsc(batch, PdfUpload.Status.PENDING);
    }

    public int uploadPdfs(MultipartFile[] files, String batch, User user) throws Exception {
        return uploadPdfs(files, batch, user, 1);
    }

    public int uploadPdfs(MultipartFile[] files, String batch, User user, int copyCount) throws Exception {
        int uploadedCount = 0;
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            // Validate file type
            if (!file.getContentType().equals("application/pdf")) {
                throw new Exception("Only PDF files are allowed");
            }
            
            // Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new Exception("File size must be less than 10MB");
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            
            // Upload to GitHub
            String githubPath = gitHubStorageService.uploadFile(file, uniqueFilename, batch);
            
            // Save to database with copy count and department info
            PdfUpload upload = new PdfUpload(
                uniqueFilename,
                originalFilename,
                githubPath,
                user.getBranch(),
                user.getDivision(),
                batch,
                file.getSize(),
                user,
                copyCount
            );
            
            pdfUploadRepository.save(upload);
            uploadedCount++;
        }
        
        return uploadedCount;
    }

    public void deletePdf(Long id, User user) throws Exception {
        PdfUpload upload = pdfUploadRepository.findById(id)
            .orElseThrow(() -> new Exception("PDF not found"));
        
        // Check if the upload belongs to the user
        if (!upload.getUser().getId().equals(user.getId())) {
            throw new Exception("Unauthorized access");
        }
        
        // Check if the upload is still pending (not processed)
        if (upload.getStatus() != PdfUpload.Status.PENDING) {
            throw new Exception("Cannot delete processed files");
        }
        
        // Delete from GitHub
        gitHubStorageService.deleteFile(upload.getGithubPath());
        
        // Delete from database
        pdfUploadRepository.delete(upload);
    }

    public void clearBatchUploads(String branch, String division, String batch) {
        List<PdfUpload> uploads = pdfUploadRepository.findByBranchAndDivisionAndBatchAndStatusOrderByUploadedAtAsc(
            branch, division, batch, PdfUpload.Status.PENDING);
        for (PdfUpload upload : uploads) {
            upload.setStatus(PdfUpload.Status.PROCESSED);
            pdfUploadRepository.save(upload);
        }
    }

    // Legacy method - delegates to the new method
    public void clearBatchUploads(String batch) {
        // This will process all pending uploads in the batch, regardless of branch/division
        List<PdfUpload> uploads = pdfUploadRepository.findByBatchAndStatusOrderByUploadedAtAsc(
            batch, PdfUpload.Status.PENDING);
        for (PdfUpload upload : uploads) {
            upload.setStatus(PdfUpload.Status.PROCESSED);
            pdfUploadRepository.save(upload);
        }
    }

    public List<PdfUpload> getPendingUploads() {
        return pdfUploadRepository.findByStatus(PdfUpload.Status.PENDING);
    }

    public long getUploadCountByUserAndBatch(Long userId, String batch) {
        return pdfUploadRepository.countByUserIdAndBatch(userId, batch);
    }

    public long getUploadCountByUser(Long userId) {
        return pdfUploadRepository.countByUserId(userId);
    }

    public Map<String, Long> getMonthlyUploadStats() {
        List<PdfUpload> allUploads = pdfUploadRepository.findAll();
        return allUploads.stream()
            .collect(Collectors.groupingBy(
                upload -> upload.getUploadedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                Collectors.counting()
            ));
    }

    public Map<String, Map<String, Long>> getBatchStatistics() {
        List<PdfUpload> allUploads = pdfUploadRepository.findAll();
        return allUploads.stream()
            .collect(Collectors.groupingBy(
                PdfUpload::getBatch,
                Collectors.groupingBy(
                    upload -> upload.getStatus().toString(),
                    Collectors.counting()
                )
            ));
    }

    public List<PdfUpload> getRecentUploads(int limit) {
        return pdfUploadRepository.findTop50ByOrderByUploadedAtDesc();
    }

    public List<PdfUpload> getAllBatchUploads(String batch) {
        return pdfUploadRepository.findByBatchOrderByUploadedAtDesc(batch);
    }

    public List<PdfUpload> getAllUploadsByStudent(Long userId) {
        return pdfUploadRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }
}
