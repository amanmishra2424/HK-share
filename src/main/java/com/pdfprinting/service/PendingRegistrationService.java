package com.pdfprinting.service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.pdfprinting.model.PendingRegistration;

/**
 * Service to manage temporary pending registrations.
 * Stores user registration data in cache until OTP verification is successful.
 * Automatically cleans up expired registrations.
 */
@Service
public class PendingRegistrationService {

    private final ConcurrentHashMap<String, PendingRegistration> pendingRegistrations = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);

    public PendingRegistrationService() {
        // Schedule cleanup of expired registrations every 5 minutes
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredRegistrations, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Store a pending registration
     */
    public void savePendingRegistration(PendingRegistration pending) {
        pendingRegistrations.put(pending.getEmail(), pending);
        System.out.println("[DEBUG] Saved pending registration for email: " + pending.getEmail() + ", OTP Expiry: " + pending.getOtpExpiry());
    }

    /**
     * Retrieve a pending registration by email
     */
    public PendingRegistration getPendingRegistration(String email) {
        return pendingRegistrations.get(email);
    }

    /**
     * Remove a pending registration after successful verification
     */
    public void removePendingRegistration(String email) {
        pendingRegistrations.remove(email);
        System.out.println("[DEBUG] Removed pending registration for email: " + email);
    }

    /**
     * Check if a pending registration exists and is not expired
     */
    public boolean hasPendingRegistration(String email) {
        PendingRegistration pending = pendingRegistrations.get(email);
        if (pending == null) {
            return false;
        }
        // Remove if expired
        if (pending.isExpired()) {
            removePendingRegistration(email);
            return false;
        }
        return true;
    }

    /**
     * Verify OTP for a pending registration
     * Returns true if OTP is valid and not expired, false otherwise
     */
    public boolean verifyOtpForPendingRegistration(String email, String enteredOtp) {
        PendingRegistration pending = getPendingRegistration(email);
        if (pending == null) {
            return false;
        }

        // Check if OTP has expired
        if (pending.isExpired()) {
            removePendingRegistration(email);
            return false;
        }

        // Verify OTP
        String entered = enteredOtp == null ? "" : enteredOtp.trim();
        boolean isValid = pending.getOtp() != null && pending.getOtp().equals(entered);

        System.out.println("[DEBUG] OTP verification for " + email + ": storedOtp=" + pending.getOtp() + 
                         ", entered=" + entered + ", isValid=" + isValid + ", expiry=" + pending.getOtpExpiry());

        return isValid;
    }

    /**
     * Resend OTP for pending registration
     */
    public void resendOtpForPendingRegistration(String email, String newOtp) {
        PendingRegistration pending = getPendingRegistration(email);
        if (pending != null && !pending.isExpired()) {
            pending.setOtp(newOtp);
            pending.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
            System.out.println("[DEBUG] Resent OTP for email: " + email + ", New expiry: " + pending.getOtpExpiry());
        }
    }

    /**
     * Clean up expired registrations from cache
     */
    private void cleanupExpiredRegistrations() {
        int sizeBefore = pendingRegistrations.size();
        pendingRegistrations.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int sizeAfter = pendingRegistrations.size();
        if (sizeBefore > sizeAfter) {
            System.out.println("[DEBUG] Cleaned up " + (sizeBefore - sizeAfter) + " expired pending registrations");
        }
    }

    /**
     * Get count of pending registrations (for monitoring)
     */
    public int getPendingRegistrationCount() {
        return pendingRegistrations.size();
    }
}
