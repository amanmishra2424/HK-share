package com.pdfprinting.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.utils.PdfMerger;
import com.pdfprinting.model.PdfUpload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        ByteArrayOutputStream mergedOutputStream = new ByteArrayOutputStream();
        PdfDocument mergedDocument = new PdfDocument(new PdfWriter(mergedOutputStream));
        PdfMerger merger = new PdfMerger(mergedDocument);

        try {
            for (PdfUpload upload : uploads) {
                try {
                    // Download PDF from GitHub
                    byte[] pdfBytes = gitHubStorageService.downloadFile(upload.getGithubPath());
                    
                    int copyCount = upload.getCopyCount();
                    
                    for (int copy = 1; copy <= copyCount; copy++) {
                        // Create PDF document from bytes for each copy
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(pdfBytes);
                        PdfDocument sourceDocument = new PdfDocument(new PdfReader(inputStream));
                        
                        // Merge all pages from source document
                        merger.merge(sourceDocument, 1, sourceDocument.getNumberOfPages());
                        
                        sourceDocument.close();
                        inputStream.close();
                    }
                    
                } catch (Exception e) {
                    System.err.println("Failed to merge PDF: " + upload.getOriginalFileName() + " - " + e.getMessage());
                    // Continue with other files
                }
            }
            
            mergedDocument.close();
            byte[] mergedPdfBytes = mergedOutputStream.toByteArray();
            
            // Cache the merged PDF for download
            mergedPdfCache.put(batchName, mergedPdfBytes);
            
            return mergedPdfBytes;
            
        } catch (Exception e) {
            mergedDocument.close();
            throw new Exception("Failed to merge PDFs: " + e.getMessage());
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
