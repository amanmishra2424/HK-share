package com.pdfprinting.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pdfprinting.model.PdfUpload;
import com.pdfprinting.model.PdfUpload.PrintType;

@Service
public class PdfMergeService {

    @Autowired
    private PdfUploadService pdfUploadService;

    @Autowired
    private GitHubStorageService gitHubStorageService;

    // Temporary storage for merged PDFs (in production, use Redis or database)
    private Map<String, byte[]> mergedPdfCache = new HashMap<>();
    
    // Storage for failed PDFs info - accessible for admin to view
    private Map<String, List<FailedPdfInfo>> failedPdfsCache = new ConcurrentHashMap<>();
    
    // Thread pool for parallel PDF downloads (improves merge performance)
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(4);
    
    /**
     * Result class for merge operations - contains merged PDF and any failures
     */
    public static class MergeResult {
        private byte[] mergedPdf;
        private List<FailedPdfInfo> failedPdfs;
        private int successCount;
        private int totalCount;
        
        public MergeResult(byte[] mergedPdf, List<FailedPdfInfo> failedPdfs, int successCount, int totalCount) {
            this.mergedPdf = mergedPdf;
            this.failedPdfs = failedPdfs;
            this.successCount = successCount;
            this.totalCount = totalCount;
        }
        
        public byte[] getMergedPdf() { return mergedPdf; }
        public List<FailedPdfInfo> getFailedPdfs() { return failedPdfs; }
        public int getSuccessCount() { return successCount; }
        public int getTotalCount() { return totalCount; }
        public int getFailedCount() { return failedPdfs.size(); }
        public boolean hasFailures() { return !failedPdfs.isEmpty(); }
    }
    
    /**
     * Information about a PDF that failed during merge
     */
    public static class FailedPdfInfo {
        private Long uploadId;
        private String fileName;
        private String studentName;
        private String rollNumber;
        private String reason;
        private String githubPath;
        private String printType;
        
        public FailedPdfInfo(PdfUpload upload, String reason) {
            this.uploadId = upload.getId();
            this.fileName = upload.getOriginalFileName();
            this.studentName = upload.getUser().getName();
            this.rollNumber = upload.getUser().getRollNumber();
            this.reason = reason;
            this.githubPath = upload.getGithubPath();
            this.printType = upload.getPrintType().getDisplayName();
        }
        
        public Long getUploadId() { return uploadId; }
        public String getFileName() { return fileName; }
        public String getStudentName() { return studentName; }
        public String getRollNumber() { return rollNumber; }
        public String getReason() { return reason; }
        public String getGithubPath() { return githubPath; }
        public String getPrintType() { return printType; }
    }

    /**
     * Generate a unique container key from the 5 container fields
     */
    public static String getContainerKey(String academicYear, String branch, String division, 
                                          String semester, String batch) {
        return String.join("|", academicYear, branch, division, semester, batch);
    }
    
    /**
     * Generate a unique container key with print type
     */
    public static String getContainerKeyWithPrintType(String academicYear, String branch, String division, 
                                                       String semester, String batch, PrintType printType) {
        return String.join("|", academicYear, branch, division, semester, batch, printType.name());
    }

    /**
     * Merge all PENDING PDFs from a specific container
     * Container is defined by (academicYear, branch, division, semester, batch)
     * PDFs are ordered by upload_time ASC
     * Uses parallel downloading for faster performance
     * Continues even if some PDFs fail - returns info about failures
     */
    public MergeResult mergeContainerPdfsWithReport(String academicYear, String branch, String division, 
                                      String semester, String batch) throws Exception {
        List<PdfUpload> uploads = pdfUploadService.getContainerUploads(
            academicYear, branch, division, semester, batch);

        if (uploads.isEmpty()) {
            throw new Exception("No PDFs found for container: " + 
                getContainerKey(academicYear, branch, division, semester, batch));
        }

        List<FailedPdfInfo> failedPdfs = new ArrayList<>();
        Map<Long, byte[]> downloadedPdfs = new ConcurrentHashMap<>();
        Map<Long, String> downloadErrors = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> downloadFutures = new ArrayList<>();
        
        // Download all PDFs in parallel
        for (PdfUpload upload : uploads) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    byte[] pdfBytes = gitHubStorageService.downloadFile(upload.getGithubPath());
                    downloadedPdfs.put(upload.getId(), pdfBytes);
                } catch (Exception e) {
                    downloadErrors.put(upload.getId(), "Download failed: " + e.getMessage());
                }
            }, downloadExecutor);
            downloadFutures.add(future);
        }
        
        CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0])).join();

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        ByteArrayOutputStream mergedOutputStream = new ByteArrayOutputStream();
        mergerUtility.setDestinationStream(mergedOutputStream);

        int successCount = 0;
        
        try {
            for (PdfUpload upload : uploads) {
                // Check for download error
                if (downloadErrors.containsKey(upload.getId())) {
                    failedPdfs.add(new FailedPdfInfo(upload, downloadErrors.get(upload.getId())));
                    continue;
                }
                
                byte[] pdfBytes = downloadedPdfs.get(upload.getId());
                if (pdfBytes == null) {
                    failedPdfs.add(new FailedPdfInfo(upload, "Download returned empty"));
                    continue;
                }

                // Validate PDF before adding
                String validationError = validatePdf(pdfBytes, upload.getOriginalFileName());
                if (validationError != null) {
                    failedPdfs.add(new FailedPdfInfo(upload, validationError));
                    continue;
                }

                try {
                    int copyCount = upload.getCopyCount();
                    for (int copy = 1; copy <= copyCount; copy++) {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
                        mergerUtility.addSource(inputStream);
                    }
                    successCount++;
                } catch (Exception e) {
                    failedPdfs.add(new FailedPdfInfo(upload, "Failed to add to merge: " + e.getMessage()));
                }
            }

            // Only merge if we have at least one successful PDF
            if (successCount == 0) {
                throw new Exception("All PDFs failed to process. Check the failed PDFs list for details.");
            }

            mergerUtility.mergeDocuments(null);
            byte[] mergedPdfBytes = mergedOutputStream.toByteArray();

            // Cache the merged PDF and failed info
            String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
            mergedPdfCache.put(containerKey, mergedPdfBytes);
            if (!failedPdfs.isEmpty()) {
                failedPdfsCache.put(containerKey, failedPdfs);
            }

            return new MergeResult(mergedPdfBytes, failedPdfs, successCount, uploads.size());

        } catch (Exception e) {
            throw new Exception("Failed to merge PDFs: " + e.getMessage(), e);
        } finally {
            try {
                mergedOutputStream.close();
            } catch (Exception ignore) {
            }
        }
    }
    
    /**
     * Legacy method - kept for backward compatibility
     */
    public byte[] mergeContainerPdfs(String academicYear, String branch, String division, 
                                      String semester, String batch) throws Exception {
        MergeResult result = mergeContainerPdfsWithReport(academicYear, branch, division, semester, batch);
        return result.getMergedPdf();
    }
    
    /**
     * Validate if a PDF is readable and not corrupt
     */
    private String validatePdf(byte[] pdfBytes, String fileName) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return "Empty PDF file";
        }
        
        try (PDDocument doc = PDDocument.load(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) {
                return "PDF has no pages";
            }
            // PDF is valid
            return null;
        } catch (Exception e) {
            return "Corrupt or invalid PDF: " + e.getMessage();
        }
    }
    
    /**
     * Merge PDFs from a specific container filtered by print type
     * Continues even if some PDFs fail - returns info about failures
     */
    public MergeResult mergeContainerPdfsByPrintTypeWithReport(String academicYear, String branch, String division, 
                                                 String semester, String batch, PrintType printType) throws Exception {
        List<PdfUpload> uploads = pdfUploadService.getContainerUploadsByPrintType(
            academicYear, branch, division, semester, batch, printType);

        if (uploads.isEmpty()) {
            throw new Exception("No " + printType.getDisplayName() + " PDFs found for container: " + 
                getContainerKey(academicYear, branch, division, semester, batch));
        }

        List<FailedPdfInfo> failedPdfs = new ArrayList<>();
        Map<Long, byte[]> downloadedPdfs = new ConcurrentHashMap<>();
        Map<Long, String> downloadErrors = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> downloadFutures = new ArrayList<>();
        
        for (PdfUpload upload : uploads) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    byte[] pdfBytes = gitHubStorageService.downloadFile(upload.getGithubPath());
                    downloadedPdfs.put(upload.getId(), pdfBytes);
                } catch (Exception e) {
                    downloadErrors.put(upload.getId(), "Download failed: " + e.getMessage());
                }
            }, downloadExecutor);
            downloadFutures.add(future);
        }
        
        CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0])).join();

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        ByteArrayOutputStream mergedOutputStream = new ByteArrayOutputStream();
        mergerUtility.setDestinationStream(mergedOutputStream);

        int successCount = 0;

        try {
            for (PdfUpload upload : uploads) {
                if (downloadErrors.containsKey(upload.getId())) {
                    failedPdfs.add(new FailedPdfInfo(upload, downloadErrors.get(upload.getId())));
                    continue;
                }
                
                byte[] pdfBytes = downloadedPdfs.get(upload.getId());
                if (pdfBytes == null) {
                    failedPdfs.add(new FailedPdfInfo(upload, "Download returned empty"));
                    continue;
                }

                String validationError = validatePdf(pdfBytes, upload.getOriginalFileName());
                if (validationError != null) {
                    failedPdfs.add(new FailedPdfInfo(upload, validationError));
                    continue;
                }

                try {
                    int copyCount = upload.getCopyCount();
                    for (int copy = 1; copy <= copyCount; copy++) {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
                        mergerUtility.addSource(inputStream);
                    }
                    successCount++;
                } catch (Exception e) {
                    failedPdfs.add(new FailedPdfInfo(upload, "Failed to add to merge: " + e.getMessage()));
                }
            }

            if (successCount == 0) {
                throw new Exception("All " + printType.getDisplayName() + " PDFs failed to process.");
            }

            mergerUtility.mergeDocuments(null);
            byte[] mergedPdfBytes = mergedOutputStream.toByteArray();

            String containerKeyWithPrintType = getContainerKeyWithPrintType(
                academicYear, branch, division, semester, batch, printType);
            mergedPdfCache.put(containerKeyWithPrintType, mergedPdfBytes);
            if (!failedPdfs.isEmpty()) {
                failedPdfsCache.put(containerKeyWithPrintType, failedPdfs);
            }

            return new MergeResult(mergedPdfBytes, failedPdfs, successCount, uploads.size());

        } catch (Exception e) {
            throw new Exception("Failed to merge " + printType.getDisplayName() + " PDFs: " + e.getMessage(), e);
        } finally {
            try {
                mergedOutputStream.close();
            } catch (Exception ignore) {
            }
        }
    }
    
    /**
     * Legacy method - kept for backward compatibility
     */
    public byte[] mergeContainerPdfsByPrintType(String academicYear, String branch, String division, 
                                                 String semester, String batch, PrintType printType) throws Exception {
        MergeResult result = mergeContainerPdfsByPrintTypeWithReport(academicYear, branch, division, semester, batch, printType);
        return result.getMergedPdf();
    }

    /**
     * Legacy method - merge by batch name only
     * @deprecated Use mergeContainerPdfs instead for proper container-based merging
     */
    @Deprecated
    public byte[] mergeBatchPdfs(String batchName) throws Exception {
        List<PdfUpload> uploads = pdfUploadService.getBatchUploads(batchName);

        if (uploads.isEmpty()) {
            throw new Exception("No PDFs found for batch: " + batchName);
        }

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        ByteArrayOutputStream mergedOutputStream = new ByteArrayOutputStream();
        mergerUtility.setDestinationStream(mergedOutputStream);

        try {
            for (PdfUpload upload : uploads) {
                try {
                    // Download PDF from GitHub
                    byte[] pdfBytes = gitHubStorageService.downloadFile(upload.getGithubPath());

                    int copyCount = upload.getCopyCount();

                    for (int copy = 1; copy <= copyCount; copy++) {
                        // Add each copy as a source stream to the merger
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
                        mergerUtility.addSource(inputStream);
                    }

                } catch (Exception e) {
                    System.err.println("Failed to add PDF to merger: " + upload.getOriginalFileName() + " - " + e.getMessage());
                    // Continue with other files
                }
            }

            // Execute the merge
            mergerUtility.mergeDocuments(null);

            byte[] mergedPdfBytes = mergedOutputStream.toByteArray();

            // Cache the merged PDF for download
            mergedPdfCache.put(batchName, mergedPdfBytes);

            return mergedPdfBytes;

        } catch (Exception e) {
            throw new Exception("Failed to merge PDFs: " + e.getMessage(), e);
        } finally {
            try {
                mergedOutputStream.close();
            } catch (Exception ignore) {
            }
        }
    }

    public int getTotalCopyCount(String academicYear, String branch, String division, 
                                  String semester, String batch) {
        List<PdfUpload> uploads = pdfUploadService.getContainerUploads(
            academicYear, branch, division, semester, batch);
        return uploads.stream().mapToInt(PdfUpload::getCopyCount).sum();
    }

    /**
     * Get merged PDF from cache using container key
     */
    public byte[] getMergedPdfByContainer(String academicYear, String branch, String division, 
                                           String semester, String batch) throws Exception {
        String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
        byte[] mergedPdf = mergedPdfCache.get(containerKey);
        if (mergedPdf == null) {
            throw new Exception("Merged PDF not found for container: " + containerKey);
        }
        return mergedPdf;
    }
    
    /**
     * Get merged PDF from cache using container key with print type
     */
    public byte[] getMergedPdfByContainerAndPrintType(String academicYear, String branch, String division, 
                                                       String semester, String batch, PrintType printType) throws Exception {
        String containerKey = getContainerKeyWithPrintType(academicYear, branch, division, semester, batch, printType);
        byte[] mergedPdf = mergedPdfCache.get(containerKey);
        if (mergedPdf == null) {
            throw new Exception("Merged " + printType.getDisplayName() + " PDF not found for container");
        }
        return mergedPdf;
    }

    /**
     * Legacy method - get merged PDF by batch name
     * @deprecated Use getMergedPdfByContainer instead
     */
    @Deprecated
    public byte[] getMergedPdf(String batchName) throws Exception {
        byte[] mergedPdf = mergedPdfCache.get(batchName);
        if (mergedPdf == null) {
            throw new Exception("Merged PDF not found for batch: " + batchName);
        }
        return mergedPdf;
    }

    public void clearMergedPdfByContainer(String academicYear, String branch, String division, 
                                           String semester, String batch) {
        String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
        mergedPdfCache.remove(containerKey);
    }
    
    public void clearMergedPdfByContainerAndPrintType(String academicYear, String branch, String division, 
                                                       String semester, String batch, PrintType printType) {
        String containerKey = getContainerKeyWithPrintType(academicYear, branch, division, semester, batch, printType);
        mergedPdfCache.remove(containerKey);
    }

    @Deprecated
    public void clearMergedPdf(String batchName) {
        mergedPdfCache.remove(batchName);
    }
    
    /**
     * Get list of failed PDFs for a container
     */
    public List<FailedPdfInfo> getFailedPdfs(String academicYear, String branch, String division, 
                                              String semester, String batch) {
        String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
        return failedPdfsCache.getOrDefault(containerKey, new ArrayList<>());
    }
    
    /**
     * Get list of failed PDFs for a container with print type
     */
    public List<FailedPdfInfo> getFailedPdfsByPrintType(String academicYear, String branch, String division, 
                                                         String semester, String batch, PrintType printType) {
        String containerKey = getContainerKeyWithPrintType(academicYear, branch, division, semester, batch, printType);
        return failedPdfsCache.getOrDefault(containerKey, new ArrayList<>());
    }
    
    /**
     * Download a specific PDF by upload ID (for downloading failed PDFs individually)
     */
    public byte[] downloadPdfById(Long uploadId) throws Exception {
        PdfUpload upload = pdfUploadService.getPdfById(uploadId);
        if (upload == null) {
            throw new Exception("PDF upload not found with ID: " + uploadId);
        }
        return gitHubStorageService.downloadFile(upload.getGithubPath());
    }
    
    /**
     * Clear failed PDFs cache for a container
     */
    public void clearFailedPdfsCache(String academicYear, String branch, String division, 
                                      String semester, String batch) {
        String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
        failedPdfsCache.remove(containerKey);
    }
}
