package com.pdfprinting.service;

import com.pdfprinting.model.User;
import com.pdfprinting.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    public void initializeAdmin() {
        try {
            if (!userRepository.existsByEmail(adminEmail)) {
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
                System.out.println("Admin user already exists with email: " + adminEmail);
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

        // Check if roll number already exists
        if (userRepository.existsByRollNumber(user.getRollNumber())) {
            throw new Exception("Roll number already registered");
        }

        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        
    // Generate OTP for email verification
    String otp = String.valueOf(100000 + (int)(Math.random() * 900000)); // 6-digit OTP
    user.setOtp(otp);
    user.setOtpExpiry(LocalDateTime.now().plusMinutes(10));
    user.setRole(User.Role.STUDENT);

    User savedUser = userRepository.save(user);

    // Send OTP email
    emailService.sendOtpEmail(savedUser);

    return savedUser;
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
}
