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
    private String academicYear; // Year of the student at upload time (denormalized, required for org)

    @Column(nullable = false)
    private String semester; // Semester at upload time (e.g., "1", "2", etc.)

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private int copyCount = 1;

    @Column(nullable = false)
    private int pageCount = 1; // Number of pages in PDF
    
    @Column(nullable = false)
    private int billedPageCount = 1; // Pages billed (may differ from pageCount for duplex with odd pages)

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCost = BigDecimal.ZERO; // Total cost for this upload

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrintType printType = PrintType.SINGLE_SIDE;

    // Constructors
    public PdfUpload() {}

    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String branch, String division, String academicYear, String semester, String batch, long fileSize, User user) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.branch = branch;
        this.division = division;
        this.academicYear = academicYear;
        this.semester = semester;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = 1; // Default copy count
        this.printType = PrintType.SINGLE_SIDE; // Default print type
    }

    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String branch, String division, String academicYear, String semester, String batch, long fileSize, User user, 
                     int copyCount, int pageCount, BigDecimal totalCost) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.branch = branch;
        this.division = division;
        this.academicYear = academicYear;
        this.semester = semester;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = copyCount;
        this.pageCount = pageCount;
        this.billedPageCount = pageCount;
        this.totalCost = totalCost;
        this.printType = PrintType.SINGLE_SIDE; // Default print type
    }
    
    public PdfUpload(String fileName, String originalFileName, String githubPath, 
                     String branch, String division, String academicYear, String semester, String batch, long fileSize, User user, 
                     int copyCount, int pageCount, int billedPageCount, BigDecimal totalCost, PrintType printType) {
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.githubPath = githubPath;
        this.branch = branch;
        this.division = division;
        this.academicYear = academicYear;
        this.semester = semester;
        this.batch = batch;
        this.fileSize = fileSize;
        this.user = user;
        this.copyCount = copyCount;
        this.pageCount = pageCount;
        this.billedPageCount = billedPageCount;
        this.totalCost = totalCost;
        this.printType = printType;
    }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

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
    
    public int getBilledPageCount() { return billedPageCount; }
    public void setBilledPageCount(int billedPageCount) { this.billedPageCount = billedPageCount; }
    
    public PrintType getPrintType() { return printType; }
    public void setPrintType(PrintType printType) { this.printType = printType; }

    public enum Status {
        PENDING, PROCESSED, DELETED
    }
    
    /**
     * Print type enum for different printing modes with associated pricing
     */
    public enum PrintType {
        SINGLE_SIDE("Single Side (B&W)", 2),      // ₹2 per page
        DOUBLE_SIDE("Double Side (Duplex)", 1),    // ₹1 per page (even pages only)
        COLOUR("Colour (Single Side)", 7);         // ₹7 per page
        
        private final String displayName;
        private final int pricePerPage;
        
        PrintType(String displayName, int pricePerPage) {
            this.displayName = displayName;
            this.pricePerPage = pricePerPage;
        }
        
        public String getDisplayName() { return displayName; }
        public int getPricePerPage() { return pricePerPage; }
    }
}
