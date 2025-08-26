package com.pdfprinting.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    private long fileSize;

    @Column(nullable = false)
    private int copyCount = 1;

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
                     String batch, long fileSize, User user) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = 1; // Default copy count
    }

    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String batch, long fileSize, User user, int copyCount) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = copyCount;
    }

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

    public enum Status {
        PENDING, PROCESSED, DELETED
    }
}
