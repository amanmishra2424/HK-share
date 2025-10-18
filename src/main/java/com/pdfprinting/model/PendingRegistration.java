package com.pdfprinting.model;

import java.time.LocalDateTime;

/**
 * Temporary registration data holder for users during the OTP verification process.
 * This stores registration details before they are saved to the database.
 * Data is kept in cache/session and removed after successful OTP verification or expiration.
 */
public class PendingRegistration {
    private String email;
    private String name;
    private String branch;
    private String division;
    private String academicYear;
    private String rollNumber;
    private String phoneNumber;
    private String batch;
    private String password; // Already encoded
    private String otp;
    private LocalDateTime otpExpiry;
    private LocalDateTime createdAt;

    public PendingRegistration() {
        this.createdAt = LocalDateTime.now();
    }

    public PendingRegistration(String email, String name, String branch, String division,
                              String rollNumber, String phoneNumber, String batch,
                              String password, String otp, LocalDateTime otpExpiry) {
        this.email = email;
        this.name = name;
        this.branch = branch;
        this.division = division;
        this.academicYear = null;
        this.rollNumber = rollNumber;
        this.phoneNumber = phoneNumber;
        this.batch = batch;
        this.password = password;
        this.otp = otp;
        this.otpExpiry = otpExpiry;
        this.createdAt = LocalDateTime.now();
    }

    public PendingRegistration(String email, String name, String branch, String division,
                              String academicYear, String rollNumber, String phoneNumber, String batch,
                              String password, String otp, LocalDateTime otpExpiry) {
        this.email = email;
        this.name = name;
        this.branch = branch;
        this.division = division;
        this.academicYear = academicYear;
        this.rollNumber = rollNumber;
        this.phoneNumber = phoneNumber;
        this.batch = batch;
        this.password = password;
        this.otp = otp;
        this.otpExpiry = otpExpiry;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getDivision() { return division; }
    public void setDivision(String division) { this.division = division; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getRollNumber() { return rollNumber; }
    public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public LocalDateTime getOtpExpiry() { return otpExpiry; }
    public void setOtpExpiry(LocalDateTime otpExpiry) { this.otpExpiry = otpExpiry; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Check if this pending registration has expired
     */
    public boolean isExpired() {
        return otpExpiry != null && LocalDateTime.now().isAfter(otpExpiry);
    }
}
