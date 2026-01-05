package com.pdfprinting.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.pdfprinting.model.PendingRegistration;
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

    @Autowired
    private PendingRegistrationService pendingRegistrationService;

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
                admin.setDivision("Admin");
                admin.setAcademicYear("Admin");
                admin.setSemester("Admin");
                admin.setRollNumber("ADMIN001");
                admin.setPhoneNumber("0000000000");
                admin.setBatch("Admin");
                admin.setPassword(passwordEncoder.encode(adminPassword));
                admin.setRole(User.Role.ADMIN);
                admin.setEmailVerified(true);
                userRepository.save(admin);
                System.out.println("Admin user created with email: " + adminEmail);
            } else {
                // Update existing admin - ensure all required fields are set
                User admin = existingAdmin.get();
                String encodedPassword = passwordEncoder.encode(adminPassword);
                admin.setPassword(encodedPassword);
                // Ensure all required fields have values (for existing admins from older schema)
                if (admin.getAcademicYear() == null || admin.getAcademicYear().isEmpty()) {
                    admin.setAcademicYear("Admin");
                }
                if (admin.getSemester() == null || admin.getSemester().isEmpty()) {
                    admin.setSemester("Admin");
                }
                if (admin.getBranch() == null || admin.getBranch().isEmpty()) {
                    admin.setBranch("Admin");
                }
                if (admin.getDivision() == null || admin.getDivision().isEmpty()) {
                    admin.setDivision("Admin");
                }
                if (admin.getRollNumber() == null || admin.getRollNumber().isEmpty()) {
                    admin.setRollNumber("ADMIN001");
                }
                if (admin.getPhoneNumber() == null || admin.getPhoneNumber().isEmpty()) {
                    admin.setPhoneNumber("0000000000");
                }
                if (admin.getBatch() == null || admin.getBatch().isEmpty()) {
                    admin.setBatch("Admin");
                }
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
        // Check if email already exists in database
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new Exception("Email already registered");
        }

        // Check if there's a pending registration with same email
        if (pendingRegistrationService.hasPendingRegistration(user.getEmail())) {
            throw new Exception("Registration already in progress for this email. Please complete OTP verification or wait 10 minutes to register again.");
        }

        // Normalize inputs (trim and uppercase branch/division) to avoid false duplicates
        String branch = user.getBranch() == null ? "" : user.getBranch().trim();
        String division = user.getDivision() == null ? "" : user.getDivision().trim();
        String academicYear = user.getAcademicYear() == null ? "" : user.getAcademicYear().trim();
        String semester = user.getSemester() == null ? "" : user.getSemester().trim();
        String roll = user.getRollNumber() == null ? "" : user.getRollNumber().trim();
        user.setBranch(branch);
        user.setDivision(division);
        user.setAcademicYear(academicYear);
        user.setSemester(semester);
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
        String encodedPassword = passwordEncoder.encode(user.getPassword());

        // Generate OTP for email verification
        String otp = String.valueOf(100000 + (int)(Math.random() * 900000)); // 6-digit OTP
        LocalDateTime otpExpiry = LocalDateTime.now().plusMinutes(10);

        try {
            // Create a pending registration (NOT saved to database yet)
            PendingRegistration pending = new PendingRegistration(
                    user.getEmail(),
                    user.getName(),
                    branch,
                    division,
                    academicYear,
                    semester,
                    roll,
                    user.getPhoneNumber(),
                    user.getBatch(),
                    encodedPassword,
                    otp,
                    otpExpiry
            );

            // Store in cache
            pendingRegistrationService.savePendingRegistration(pending);

            // Send OTP email
            // Create a temporary user object just for email sending
            User tempUser = new User();
            tempUser.setEmail(user.getEmail());
            tempUser.setName(user.getName());
            tempUser.setOtp(otp);
            emailService.sendOtpEmail(tempUser);

            System.out.println("[DEBUG] Pending registration created for email: " + user.getEmail() + 
                             " (NOT saved to database yet). Waiting for OTP verification...");

            // Return the user object (not saved to DB, just for controller use)
            user.setOtp(otp);
            user.setOtpExpiry(otpExpiry);
            return user;

        } catch (Exception e) {
            System.err.println("Error in registration process: " + e.getMessage());
            throw new Exception("Registration failed. Please try again. Error: " + e.getMessage());
        }
    }


    public boolean verifyOtp(String email, String otp) {
        // First, check if this is a pending registration
        if (pendingRegistrationService.hasPendingRegistration(email)) {
            boolean isValid = pendingRegistrationService.verifyOtpForPendingRegistration(email, otp);
            if (isValid) {
                // OTP is correct, now save the user to database
                try {
                    PendingRegistration pending = pendingRegistrationService.getPendingRegistration(email);
                    User user = createUserFromPendingRegistration(pending);
                    User savedUser = userRepository.save(user);

                    // Create wallet for the new user
                    walletService.getOrCreateWallet(savedUser);

                    // Send welcome email
                    try {
                        emailService.sendWelcomeEmail(savedUser);
                    } catch (Exception ignored) {}

                    // Remove from pending registrations after successful save
                    pendingRegistrationService.removePendingRegistration(email);

                    System.out.println("[DEBUG] User successfully registered and verified: " + email);
                    return true;
                } catch (DataIntegrityViolationException dive) {
                    System.err.println("Error saving verified user: " + dive.getMessage());
                    // Don't remove pending registration, let user retry
                    return false;
                } catch (Exception e) {
                    System.err.println("Error finalizing registration: " + e.getMessage());
                    return false;
                }
            }
            return false;
        }

        // Fall back to checking already registered users (for backward compatibility)
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String entered = otp == null ? "" : otp.trim();
            System.out.println("[DEBUG] Verifying OTP for already-registered user " + email + ": storedOtp=" + user.getOtp() + ", entered=" + entered + ", expiry=" + user.getOtpExpiry());

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

    /**
     * Create a User entity from pending registration data
     */
    private User createUserFromPendingRegistration(PendingRegistration pending) {
        User user = new User();
        user.setEmail(pending.getEmail());
        user.setName(pending.getName());
        user.setBranch(pending.getBranch());
        user.setDivision(pending.getDivision());
        user.setAcademicYear(pending.getAcademicYear());
        user.setSemester(pending.getSemester());
        user.setRollNumber(pending.getRollNumber());
        user.setPhoneNumber(pending.getPhoneNumber());
        user.setBatch(pending.getBatch());
        user.setPassword(pending.getPassword()); // Already encoded
        user.setRole(User.Role.STUDENT);
        user.setEmailVerified(true); // Mark as verified since OTP was verified
        return user;
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
        // First check if there's a pending registration
        if (pendingRegistrationService.hasPendingRegistration(email)) {
            String newOtp = String.valueOf(100000 + (int)(Math.random() * 900000));
            pendingRegistrationService.resendOtpForPendingRegistration(email, newOtp);
            
            // Send OTP email
            User tempUser = new User();
            tempUser.setEmail(email);
            tempUser.setOtp(newOtp);
            emailService.sendOtpEmail(tempUser);
            
            System.out.println("[DEBUG] Resent OTP for pending registration: " + email);
            return;
        }

        // Fall back to already registered users
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new Exception("No pending or registered user found with email: " + email);
        }
        User user = userOpt.get();
        String otp = String.valueOf(100000 + (int)(Math.random() * 900000));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
        userRepository.save(user);
        emailService.sendOtpEmail(user);
        System.out.println("[DEBUG] Resent OTP for registered user: " + email);
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
