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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pdfprinting.model.PdfUpload;

@Service
public class PdfMergeService {

    @Autowired
    private PdfUploadService pdfUploadService;

    @Autowired
    private GitHubStorageService gitHubStorageService;

    // Temporary storage for merged PDFs (in production, use Redis or database)
    private Map<String, byte[]> mergedPdfCache = new HashMap<>();
    
    // Thread pool for parallel PDF downloads (improves merge performance)
    private static final ExecutorService downloadExecutor = Executors.newFixedThreadPool(4);

    /**
     * Generate a unique container key from the 5 container fields
     */
    public static String getContainerKey(String academicYear, String branch, String division, 
                                          String semester, String batch) {
        return String.join("|", academicYear, branch, division, semester, batch);
    }

    /**
     * Merge all PENDING PDFs from a specific container
     * Container is defined by (academicYear, branch, division, semester, batch)
     * PDFs are ordered by upload_time ASC
     * Uses parallel downloading for faster performance
     */
    public byte[] mergeContainerPdfs(String academicYear, String branch, String division, 
                                      String semester, String batch) throws Exception {
        List<PdfUpload> uploads = pdfUploadService.getContainerUploads(
            academicYear, branch, division, semester, batch);

        if (uploads.isEmpty()) {
            throw new Exception("No PDFs found for container: " + 
                getContainerKey(academicYear, branch, division, semester, batch));
        }

        // Download all PDFs in parallel for faster performance
        Map<Long, byte[]> downloadedPdfs = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> downloadFutures = new ArrayList<>();
        
        for (PdfUpload upload : uploads) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    byte[] pdfBytes = gitHubStorageService.downloadFile(upload.getGithubPath());
                    downloadedPdfs.put(upload.getId(), pdfBytes);
                } catch (Exception e) {
                    System.err.println("Failed to download PDF: " + upload.getOriginalFileName() + " - " + e.getMessage());
                }
            }, downloadExecutor);
            downloadFutures.add(future);
        }
        
        // Wait for all downloads to complete
        CompletableFuture.allOf(downloadFutures.toArray(new CompletableFuture[0])).join();

        PDFMergerUtility mergerUtility = new PDFMergerUtility();
        ByteArrayOutputStream mergedOutputStream = new ByteArrayOutputStream();
        mergerUtility.setDestinationStream(mergedOutputStream);

        try {
            // Add PDFs in order (uploads are already ordered by uploadedAt ASC)
            for (PdfUpload upload : uploads) {
                byte[] pdfBytes = downloadedPdfs.get(upload.getId());
                if (pdfBytes == null) {
                    System.err.println("Skipping PDF (download failed): " + upload.getOriginalFileName());
                    continue;
                }

                int copyCount = upload.getCopyCount();
                for (int copy = 1; copy <= copyCount; copy++) {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
                    mergerUtility.addSource(inputStream);
                }
            }

            // Execute the merge
            mergerUtility.mergeDocuments(null);

            byte[] mergedPdfBytes = mergedOutputStream.toByteArray();

            // Cache the merged PDF for download using container key
            String containerKey = getContainerKey(academicYear, branch, division, semester, batch);
            mergedPdfCache.put(containerKey, mergedPdfBytes);

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

    @Deprecated
    public void clearMergedPdf(String batchName) {
        mergedPdfCache.remove(batchName);
    }
}
