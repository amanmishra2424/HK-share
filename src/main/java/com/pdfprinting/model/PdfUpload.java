package com.pdfprinting.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "pdf_uploads")
public class PdfUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String githubPath;

    @Column(nullable = false)
    private String batch;

    @Column(nullable = false)
    private String branch;

    @Column(nullable = false)
    private String division;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private int copyCount = 1;

    @Column(nullable = false)
    private int pageCount = 1; // Number of pages in PDF

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO; // Total cost for this upload

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    // Constructors
    public PdfUpload() {}

    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String branch, String division, String batch, long fileSize, User user) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.branch = branch;
        this.division = division;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = 1; // Default copy count
    }

    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String branch, String division, String batch, long fileSize, User user, 
                     int copyCount, int pageCount, BigDecimal totalCost) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.branch = branch;
        this.division = division;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = copyCount;
        this.pageCount = pageCount;
        this.totalCost = totalCost;
    }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getGithubPath() { return githubPath; }
    public void setGithubPath(String githubPath) { this.githubPath = githubPath; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public int getCopyCount() { return copyCount; }
    public void setCopyCount(int copyCount) { this.copyCount = copyCount; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public enum Status {
        PENDING, PROCESSED, DELETED
    }
}
