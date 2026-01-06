package com.pdfprinting.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.User;
import com.pdfprinting.repository.PdfUploadRepository;

@Service
public class PdfUploadService {

    @Autowired
    private PdfUploadRepository pdfUploadRepository;

    @Autowired
    private GitHubStorageService gitHubStorageService;

    public List<PdfUpload> getUserUploads(User user) {
        return pdfUploadRepository.findByUserOrderByUploadedAtDesc(user);
    }

    /**
     * Get only PENDING uploads for a user - optimized to not fetch already processed records
     * This reduces DB load after admin merges PDFs
     */
    public List<PdfUpload> getUserPendingUploads(User user) {
        return pdfUploadRepository.findByUserAndStatusOrderByUploadedAtDesc(user, PdfUpload.Status.PENDING);
    }

    /**
     * Get uploads from a specific container (academic_year, branch, division, semester, batch)
     * This is the primary method for container-based retrieval
     */
    public List<PdfUpload> getContainerUploads(String academicYear, String branch, String division, 
                                                String semester, String batch) {
        return pdfUploadRepository.findByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndStatusOrderByUploadedAtAsc(
            academicYear, branch, division, semester, batch, PdfUpload.Status.PENDING);
    }

    /**
     * Legacy method - get uploads by batch name only
     * @deprecated Use getContainerUploads instead for proper container-based queries
     */
    @Deprecated
    public List<PdfUpload> getBatchUploads(String batch) {
        return pdfUploadRepository.findByBatchAndStatusOrderByUploadedAtAsc(batch, PdfUpload.Status.PENDING);
    }

    public int uploadPdfs(MultipartFile[] files, String batch, User user) throws Exception {
        return uploadPdfs(files, batch, user, 1);
    }

    public int uploadPdfs(MultipartFile[] files, String batch, User user, int copyCount) throws Exception {
        // Validate user has all container fields set
        if (user.getAcademicYear() == null || user.getAcademicYear().isBlank()) {
            throw new Exception("Your academic year is not set. Please update your profile before uploading PDFs.");
        }
        if (user.getSemester() == null || user.getSemester().isBlank()) {
            throw new Exception("Your semester is not set. Please update your profile before uploading PDFs.");
        }
        if (user.getBranch() == null || user.getBranch().isBlank()) {
            throw new Exception("Your branch is not set. Please update your profile before uploading PDFs.");
        }
        if (user.getDivision() == null || user.getDivision().isBlank()) {
            throw new Exception("Your division is not set. Please update your profile before uploading PDFs.");
        }
        
        int uploadedCount = 0;
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            
            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !contentType.equals("application/pdf")) {
                throw new Exception("Only PDF files are allowed");
            }
            
            // Validate file size (10MB limit)
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new Exception("File size must be less than 10MB");
            }
            
            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isEmpty()) {
                throw new Exception("Invalid file name");
            }
            String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;
            
            // Count actual PDF pages using PDFBox
            int pageCount = countPdfPages(file);
            
            // Upload to GitHub
            String githubPath = gitHubStorageService.uploadFile(file, uniqueFilename, batch);
            
            // Calculate billing info using actual page count
            // Pricing: ₹2 per page per copy
            BigDecimal totalCost = BigDecimal.valueOf(2).multiply(
                BigDecimal.valueOf(pageCount).multiply(BigDecimal.valueOf(copyCount))
            );
            
            // Save to database with copy count, billing info and department info
            // Upload goes directly into the container defined by (academicYear, branch, division, semester, batch)
            PdfUpload upload = new PdfUpload(
                uniqueFilename,
                originalFilename,
                githubPath,
                user.getBranch(),
                user.getDivision(),
                user.getAcademicYear(),
                user.getSemester(),
                batch,
                file.getSize(),
                user,
                copyCount,
                pageCount,
                totalCost
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

    /**
     * Mark all uploads in the container as PROCESSED
     * Container is defined by (academicYear, branch, division, semester, batch)
     */
    public void clearContainerUploads(String academicYear, String branch, String division, 
                                       String semester, String batch) {
        List<PdfUpload> uploads = pdfUploadRepository.findByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndStatusOrderByUploadedAtAsc(
            academicYear, branch, division, semester, batch, PdfUpload.Status.PENDING);
        for (PdfUpload upload : uploads) {
            upload.setStatus(PdfUpload.Status.PROCESSED);
            pdfUploadRepository.save(upload);
        }
    }

    /**
     * @deprecated Use clearContainerUploads instead
     */
    @Deprecated
    public void clearBatchUploads(String branch, String division, String batch) {
        List<PdfUpload> uploads = pdfUploadRepository.findByBranchAndDivisionAndBatchAndStatusOrderByUploadedAtAsc(
            branch, division, batch, PdfUpload.Status.PENDING);
        for (PdfUpload upload : uploads) {
            upload.setStatus(PdfUpload.Status.PROCESSED);
            pdfUploadRepository.save(upload);
        }
    }

    /**
     * Legacy method - marks all pending uploads with this batch name as processed
     * @deprecated Use clearContainerUploads instead for proper container-based clearing
     */
    @Deprecated
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

    /**
     * Group statistics by composite key: Year|Branch|Division|Semester|Batch -> Status counts
     * Container key format: academicYear|branch|division|semester|batch
     */
    public Map<String, Map<String, Long>> getBatchStatistics() {
        List<PdfUpload> allUploads = pdfUploadRepository.findAll();
        return allUploads.stream()
            .collect(Collectors.groupingBy(
                upload -> String.join("|",
                    safe(upload.getAcademicYear()),
                    safe(upload.getBranch()),
                    safe(upload.getDivision()),
                    safe(upload.getSemester()),
                    safe(upload.getBatch())
                ),
                Collectors.groupingBy(u -> u.getStatus().toString(), Collectors.counting())
            ));
    }

    private String safe(String v) { return v == null ? "" : v; }

    public List<PdfUpload> getRecentUploads(int limit) {
        return pdfUploadRepository.findTop50ByOrderByUploadedAtDesc();
    }

    public List<PdfUpload> getAllBatchUploads(String batch) {
        return pdfUploadRepository.findByBatchOrderByUploadedAtDesc(batch);
    }

    public List<PdfUpload> getAllUploadsByStudent(Long userId) {
        return pdfUploadRepository.findByUserIdOrderByUploadedAtDesc(userId);
    }

    public PdfUpload getPdfById(Long id) {
        return pdfUploadRepository.findById(id).orElse(null);
    }

    public int calculateTotalPages(MultipartFile[] files) throws Exception {
        int totalPages = 0;
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                String contentType = file.getContentType();
                if (contentType != null && contentType.equals("application/pdf")) {
                    totalPages += countPdfPages(file);
                }
            }
        }
        return totalPages;
    }
    
    /**
     * Count the actual number of pages in a PDF file using Apache PDFBox
     * @param file the PDF file to analyze
     * @return number of pages in the PDF
     * @throws Exception if PDF cannot be read or processed
     */
    private int countPdfPages(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new Exception("Invalid PDF: No pages found in " + file.getOriginalFilename());
            }
            return pageCount;
        } catch (IOException e) {
            throw new Exception("Failed to read PDF file: " + file.getOriginalFilename() + ". Error: " + e.getMessage());
        }
    }
}
