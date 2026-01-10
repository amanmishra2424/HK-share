package com.pdfprinting.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.PdfUpload.PrintType;
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
     * Get uploads from a specific container filtered by print type
     */
    public List<PdfUpload> getContainerUploadsByPrintType(String academicYear, String branch, String division, 
                                                           String semester, String batch, PrintType printType) {
        return pdfUploadRepository.findByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndPrintTypeAndStatusOrderByUploadedAtAsc(
            academicYear, branch, division, semester, batch, printType, PdfUpload.Status.PENDING);
    }
    
    /**
     * Count uploads by container and print type
     */
    public long countContainerUploadsByPrintType(String academicYear, String branch, String division,
                                                  String semester, String batch, PrintType printType) {
        return pdfUploadRepository.countByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndPrintTypeAndStatus(
            academicYear, branch, division, semester, batch, printType, PdfUpload.Status.PENDING);
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
        return uploadPdfs(files, batch, user, 1, PrintType.SINGLE_SIDE);
    }

    public int uploadPdfs(MultipartFile[] files, String batch, User user, int copyCount) throws Exception {
        return uploadPdfs(files, batch, user, copyCount, PrintType.SINGLE_SIDE);
    }
    
    public int uploadPdfs(MultipartFile[] files, String batch, User user, int copyCount, PrintType printType) throws Exception {
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
            
            // Calculate billed pages (for duplex, rounds up odd to even)
            int billedPageCount = pageCount;
            if (printType == PrintType.DOUBLE_SIDE && pageCount % 2 != 0) {
                billedPageCount = pageCount + 1;
            }
            
            // Process file - add blank page for duplex if needed
            byte[] pdfBytes;
            long finalFileSize;
            if (printType == PrintType.DOUBLE_SIDE && pageCount % 2 != 0) {
                // Add blank page to end of PDF for proper duplex alignment
                pdfBytes = addBlankPageToPdf(file);
                finalFileSize = pdfBytes.length;
            } else {
                pdfBytes = file.getBytes();
                finalFileSize = file.getSize();
            }
            
            // Upload to GitHub (using byte array if modified)
            String githubPath;
            if (printType == PrintType.DOUBLE_SIDE && pageCount % 2 != 0) {
                githubPath = gitHubStorageService.uploadFileBytes(pdfBytes, uniqueFilename, batch);
            } else {
                githubPath = gitHubStorageService.uploadFile(file, uniqueFilename, batch);
            }
            
            // Calculate billing info using billed page count and print type pricing
            BigDecimal pricePerPage = BigDecimal.valueOf(printType.getPricePerPage());
            BigDecimal totalCost = pricePerPage.multiply(
                BigDecimal.valueOf(billedPageCount).multiply(BigDecimal.valueOf(copyCount))
            );
            
            // Save to database with copy count, billing info, print type and department info
            PdfUpload upload = new PdfUpload(
                uniqueFilename,
                originalFilename,
                githubPath,
                user.getBranch(),
                user.getDivision(),
                user.getAcademicYear(),
                user.getSemester(),
                batch,
                finalFileSize,
                user,
                copyCount,
                pageCount,
                billedPageCount,
                totalCost,
                printType
            );
            
            pdfUploadRepository.save(upload);
            uploadedCount++;
        }
        
        return uploadedCount;
    }
    
    /**
     * Add a blank page to the end of a PDF for proper duplex printing alignment
     */
    private byte[] addBlankPageToPdf(MultipartFile file) throws Exception {
        try (PDDocument document = PDDocument.load(file.getInputStream());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            // Get the last page's size to match the blank page
            PDPage lastPage = document.getPage(document.getNumberOfPages() - 1);
            PDRectangle pageSize = lastPage.getMediaBox();
            
            // Add a blank page with the same size
            PDPage blankPage = new PDPage(pageSize);
            document.addPage(blankPage);
            
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new Exception("Failed to add blank page to PDF: " + e.getMessage());
        }
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
    @Transactional
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
     * Mark uploads in the container with specific print type as PROCESSED
     */
    @Transactional
    public void clearContainerUploadsByPrintType(String academicYear, String branch, String division, 
                                                  String semester, String batch, PrintType printType) {
        List<PdfUpload> uploads = pdfUploadRepository.findByAcademicYearAndBranchAndDivisionAndSemesterAndBatchAndPrintTypeAndStatusOrderByUploadedAtAsc(
            academicYear, branch, division, semester, batch, printType, PdfUpload.Status.PENDING);
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
     * Calculate total cost for files based on print type
     */
    public BigDecimal calculateTotalCost(MultipartFile[] files, int copyCount, PrintType printType) throws Exception {
        int totalPages = calculateTotalPages(files);
        int billedPages = totalPages;
        
        // For duplex, each file's odd pages are rounded up individually
        // But for pre-calculation, we approximate with total pages
        if (printType == PrintType.DOUBLE_SIDE) {
            // For accurate pre-calculation, count each file individually
            billedPages = 0;
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    String contentType = file.getContentType();
                    if (contentType != null && contentType.equals("application/pdf")) {
                        int filePages = countPdfPages(file);
                        // Round up odd pages to even for each file
                        if (filePages % 2 != 0) {
                            filePages += 1;
                        }
                        billedPages += filePages;
                    }
                }
            }
        }
        
        BigDecimal pricePerPage = BigDecimal.valueOf(printType.getPricePerPage());
        return pricePerPage.multiply(BigDecimal.valueOf(billedPages))
                          .multiply(BigDecimal.valueOf(copyCount));
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
