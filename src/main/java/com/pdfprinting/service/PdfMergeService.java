package com.pdfprinting.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                        // PDFMergerUtility will read the streams when merging; streams are closed by PDFBox internals
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
            // cleanup is handled by streams, but ensure the output stream is closed
            try {
                mergedOutputStream.close();
            } catch (Exception ignore) {
            }
        }
    }

    public int getTotalCopyCount(String batchName) {
        List<PdfUpload> uploads = pdfUploadService.getBatchUploads(batchName);
        return uploads.stream().mapToInt(PdfUpload::getCopyCount).sum();
    }

    public byte[] getMergedPdf(String batchName) throws Exception {
        byte[] mergedPdf = mergedPdfCache.get(batchName);
        if (mergedPdf == null) {
            throw new Exception("Merged PDF not found for batch: " + batchName);
        }
        return mergedPdf;
    }

    public void clearMergedPdf(String batchName) {
        mergedPdfCache.remove(batchName);
    }
}
