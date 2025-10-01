package com.pdfprinting.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pdfprinting.model.User;
import com.pdfprinting.repository.UserRepository;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private WalletService walletService;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    public void initializeAdmin() {
        try {
            Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);
            if (!existingAdmin.isPresent()) {
                // Create new admin user
                User admin = new User();
                admin.setName("Administrator");
                admin.setEmail(adminEmail);
                admin.setBranch("Admin");
                admin.setRollNumber("ADMIN001");
                admin.setPhoneNumber("0000000000");
                admin.setBatch("Admin");
                admin.setDivision("Admin");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                admin.setRole(User.Role.ADMIN);
                admin.setEmailVerified(true);
                userRepository.save(admin);
                System.out.println("Admin user created with email: " + adminEmail);
            } else {
                // Update existing admin password to match application.properties
                User admin = existingAdmin.get();
                String encodedPassword = passwordEncoder.encode(adminPassword);
                admin.setPassword(encodedPassword);
                userRepository.save(admin);
                System.out.println("Admin user already exists with email: " + adminEmail + " - password updated");
            }
        } catch (Exception e) {
            System.err.println("Error initializing admin user: " + e.getMessage());
            e.printStackTrace();
            // Don't rethrow the exception to prevent application startup failure
        }
    }

    public User registerUser(User user) throws Exception {
        // Check if email already exists
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new Exception("Email already registered");
        }
        // Normalize inputs (trim and uppercase branch/division) to avoid false duplicates
        String branch = user.getBranch() == null ? "" : user.getBranch().trim();
        String division = user.getDivision() == null ? "" : user.getDivision().trim();
        String roll = user.getRollNumber() == null ? "" : user.getRollNumber().trim();
        user.setBranch(branch);
        user.setDivision(division);
        user.setRollNumber(roll);

        // Check if roll number exists in the same branch and division
        try {
            if (userRepository.existsByBranchAndDivisionAndRollNumber(branch, division, roll)) {
                throw new Exception("Roll number '" + roll + "' already exists in division " + division + " of " + branch + " branch.");
            }
        } catch (Exception e) {
            System.err.println("Error checking roll number: " + e.getMessage());
            throw new Exception("Error validating roll number. Please try again. If the problem persists, contact support.");
        }

        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Generate OTP for email verification
        String otp = String.valueOf(100000 + (int)(Math.random() * 900000)); // 6-digit OTP
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        user.setRole(User.Role.STUDENT);

        try {
            // Debug log: show values being saved to help diagnose duplicate constraint
            System.out.println("[DEBUG] Attempting to save user: branch='" + user.getBranch() + "', division='" + user.getDivision() + "', rollNumber='" + user.getRollNumber() + "', email='" + user.getEmail() + "'");
            User savedUser = userRepository.save(user);

            // Create wallet for the new user
            walletService.getOrCreateWallet(savedUser);

            // Send OTP email
            emailService.sendOtpEmail(savedUser);

            return savedUser;
        } catch (DataIntegrityViolationException dive) {
            // Translate common DB constraint errors to user-friendly messages
            String msg = dive.getMostSpecificCause() != null ? dive.getMostSpecificCause().getMessage() : dive.getMessage();
            if (msg != null && msg.contains("users.UKbav7qiaas16cr7jn0eb4n7fy0")) {
                // unique index on branch+division+rollNumber violated
                throw new Exception("Roll number '" + user.getRollNumber() + "' already exists in division " + user.getDivision() + " of " + user.getBranch() + " branch.");
            }
            // If DB says duplicate entry but index name not present, check fallback: global roll number
            if (msg != null && msg.toLowerCase().contains("duplicate") || userRepository.existsByRollNumber(user.getRollNumber())) {
                throw new Exception("Roll number '" + user.getRollNumber() + "' appears to be already registered in the same department and division. If you believe this is incorrect, please contact support.");
            }
            // Unknown data integrity issue
            System.err.println("DataIntegrityViolation while saving user: " + msg);
            throw new Exception("Failed to register user due to database constraint. Please verify your input or contact support.");
        }
    }


    public boolean verifyOtp(String email, String otp) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String entered = otp == null ? "" : otp.trim();
            // Debug logging (temporary) - helps diagnose OTP issues
            System.out.println("[DEBUG] Verifying OTP for " + email + ": storedOtp=" + user.getOtp() + ", entered=" + entered + ", expiry=" + user.getOtpExpiry());

            if (user.getOtp() != null && user.getOtpExpiry() != null &&
                user.getOtp().equals(entered) && user.getOtpExpiry().isAfter(LocalDateTime.now())) {
                user.setEmailVerified(true);
                user.setOtp(null);
                user.setOtpExpiry(null);
                userRepository.save(user);
                // send welcome email
                try {
                    emailService.sendWelcomeEmail(user);
                } catch (Exception ignored) {}
                return true;
            }
        }
        return false;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Reset admin password manually (useful for debugging login issues)
     */
    public void resetAdminPassword() {
        try {
            Optional<User> adminUser = userRepository.findByEmail(adminEmail);
            if (adminUser.isPresent()) {
                User admin = adminUser.get();
                admin.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(admin);
                System.out.println("Admin password reset successfully for: " + adminEmail);
            } else {
                System.out.println("Admin user not found: " + adminEmail);
            }
        } catch (Exception e) {
            System.err.println("Error resetting admin password: " + e.getMessage());
        }
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public void resendOtp(String email) throws Exception {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new Exception("No user found with email: " + email);
        }
        User user = userOpt.get();
        String otp = String.valueOf(100000 + (int)(Math.random() * 900000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);
        emailService.sendOtpEmail(user);
    }

    public List<User> getStudentsByBatch(String batch) {
        return userRepository.findByBatchAndRole(batch, User.Role.STUDENT);
    }

    public List<User> getAllStudents() {
        return userRepository.findByRole(User.Role.STUDENT);
    }

    public User getStudentById(Long studentId) {
        Optional<User> userOpt = userRepository.findById(studentId);
        if (userOpt.isPresent() && userOpt.get().getRole() == User.Role.STUDENT) {
            return userOpt.get();
        }
        return null;
    }
}
