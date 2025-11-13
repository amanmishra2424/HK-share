// ...existing code...

// ...existing code...
package com.pdfprinting.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.pdfprinting.model.User;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private TemplateEngine templateEngine;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.mail.from-address:${spring.mail.from:${spring.mail.username:}}}")
    private String configuredFromEmail;

    @Value("${app.mail.from-name:Print For You}")
    private String fromName;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    private boolean isEmailConfigured() {
        if (!emailEnabled || mailSender == null) {
            return false;
        }

        String fromAddress = resolveFromEmail();

        return StringUtils.hasText(mailUsername) && !mailUsername.contains("${")
                && StringUtils.hasText(fromAddress) && !fromAddress.contains("${")
                && !"your-email@gmail.com".equalsIgnoreCase(fromAddress)
                && !"no-reply@example.com".equalsIgnoreCase(fromAddress);
    }

    private String resolveFromEmail() {
        if (StringUtils.hasText(configuredFromEmail) && !configuredFromEmail.contains("${")) {
            return configuredFromEmail.trim();
        }

        if (StringUtils.hasText(mailUsername) && !mailUsername.contains("${")) {
            return mailUsername.trim();
        }

        return "";
    }

    public void sendVerificationEmail(User user) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping verification email for user: {}", user.getEmail());
            return;
        }

        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending verification email to {} (attempt {}/{})", user.getEmail(), attempt, MAX_RETRIES);
                
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                String fromEmail = resolveFromEmail();
                helper.setFrom(fromEmail, fromName);
                helper.setTo(user.getEmail());
                helper.setSubject("Welcome to Print For You - Please Verify Your Email");
                
                if (templateEngine != null) {
                    // Create template context
                    Context context = new Context();
                    context.setVariable("user", user);
                    context.setVariable("verificationUrl", baseUrl + "/verify-email?token=" + user.getVerificationToken());
                    context.setVariable("baseUrl", baseUrl);
                    
                    // Process HTML template
                    String htmlContent = templateEngine.process("email/verification", context);
                    helper.setText(htmlContent, true);
                } else {
                    // Fallback to plain text
                    String textContent = "Welcome to Print For You!\n\n" +
                                       "Please verify your email by clicking the following link:\n" +
                                       baseUrl + "/verify-email?token=" + user.getVerificationToken() + "\n\n" +
                                       "Thank you!";
                    helper.setText(textContent, false);
                }
                
                mailSender.send(message);
                logger.info("Verification email sent successfully to {}", user.getEmail());
                return;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Failed to send verification email to {} (attempt {}/{}): {}", 
                          user.getEmail(), attempt, MAX_RETRIES, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("Email sending interrupted for {}", user.getEmail());
                        return;
                    }
                }
            }
        }
        
        logger.error("Failed to send verification email to {} after {} attempts: {}", 
                    user.getEmail(), MAX_RETRIES, 
                    lastException != null ? lastException.getMessage() : "Unknown error");
    }

    public void sendBatchProcessedNotification(String batchName, int fileCount, List<String> studentEmails) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping batch notification for: {}", batchName);
            return;
        }

        try {
            logger.info("Sending batch processed notification for {} with {} files", batchName, fileCount);
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = resolveFromEmail();
            helper.setFrom(fromEmail, fromName);
            helper.setTo(fromEmail); // Send to admin
            helper.setSubject("Batch Processed: " + batchName + " (" + fileCount + " files)");
            
            if (templateEngine != null) {
                // Create template context
                Context context = new Context();
                context.setVariable("batchName", batchName);
                context.setVariable("fileCount", fileCount);
                context.setVariable("studentEmails", studentEmails);
                context.setVariable("processedAt", java.time.LocalDateTime.now());
                
                // Process HTML template
                String htmlContent = templateEngine.process("email/batch-processed", context);
                helper.setText(htmlContent, true);
            } else {
                // Fallback to plain text
                String textContent = "Batch Processed: " + batchName + "\n\n" +
                                   "Files processed: " + fileCount + "\n" +
                                   "Student emails: " + String.join(", ", studentEmails) + "\n" +
                                   "Processed at: " + java.time.LocalDateTime.now();
                helper.setText(textContent, false);
            }
            
            mailSender.send(message);
            logger.info("Batch processed notification sent successfully for {}", batchName);
            
        } catch (Exception e) {
            logger.error("Failed to send batch processed notification for {}: {}", batchName, e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping welcome email for user: {}", user.getEmail());
            return;
        }

        try {
            logger.info("Sending welcome email to {}", user.getEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = resolveFromEmail();
            helper.setFrom(fromEmail, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to Print For You - Account Activated!");
            
            if (templateEngine != null) {
                // Create template context
                Context context = new Context();
                context.setVariable("user", user);
                context.setVariable("loginUrl", baseUrl + "/login");
                context.setVariable("baseUrl", baseUrl);
                
                // Process HTML template
                String htmlContent = templateEngine.process("email/welcome", context);
                helper.setText(htmlContent, true);
            } else {
                // Fallback to plain text
                String textContent = "Welcome to Print For You!\n\n" +
                                   "Your account has been activated.\n\n" +
                                   "Please log in by clicking the following link:\n" +
                                   baseUrl + "/login\n\n" +
                                   "Thank you!";
                helper.setText(textContent, false);
            }
            
            mailSender.send(message);
            logger.info("Welcome email sent successfully to {}", user.getEmail());
            
        } catch (Exception e) {
            logger.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping password reset email for user: {}", user.getEmail());
            return;
        }

        try {
            logger.info("Sending password reset email to {}", user.getEmail());
            
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String fromEmail = resolveFromEmail();
            helper.setFrom(fromEmail, fromName);
            helper.setTo(user.getEmail());
            helper.setSubject("Print For You - Password Reset Request");
            
            if (templateEngine != null) {
                // Create template context
                Context context = new Context();
                context.setVariable("user", user);
                context.setVariable("resetUrl", baseUrl + "/reset-password?token=" + resetToken);
                context.setVariable("baseUrl", baseUrl);
                
                // Process HTML template
                String htmlContent = templateEngine.process("email/password-reset", context);
                helper.setText(htmlContent, true);
            } else {
                // Fallback to plain text
                String textContent = "Hello " + user.getName() + ",\n\n" +
                                   "You have requested to reset your password.\n\n" +
                                   "Please click the following link to reset your password:\n" +
                                   baseUrl + "/reset-password?token=" + resetToken + "\n\n" +
                                   "Thank you!";
                helper.setText(textContent, false);
            }
            
            mailSender.send(message);
            logger.info("Password reset email sent successfully to {}", user.getEmail());
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public boolean testEmailConfiguration() {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Cannot test email configuration.");
            return false;
        }

        try {
            logger.info("Testing email configuration...");
            
            String fromEmail = resolveFromEmail();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send test email to self
            message.setSubject("Print For You - Email Configuration Test");
            message.setText("This is a test email to verify that the email configuration is working correctly.\n\n" +
                          "If you receive this email, the email system is properly configured.\n\n" +
                          "Timestamp: " + java.time.LocalDateTime.now());
            
            mailSender.send(message);
            logger.info("Test email sent successfully");
            return true;
            
        } catch (Exception e) {
            logger.error("Email configuration test failed: {}", e.getMessage());
            return false;
        }
    }

    @Async
    public void sendOtpEmail(User user) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping OTP email for user: {}", user.getEmail());
            return;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending OTP email to {} (attempt {}/{})", user.getEmail(), attempt, MAX_RETRIES);
                String fromEmail = resolveFromEmail();

                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setFrom(fromEmail);
                message.setSubject("Your OTP for Print For You Registration");
                message.setText("Your OTP for registration is: " + user.getOtp() + "\nThis OTP is valid for 10 minutes.");
                mailSender.send(message);
                logger.info("OTP email sent to {}", user.getEmail());
                return;
            } catch (Exception e) {
                lastException = e;
                logger.error("Failed to send OTP email to {}: {}", user.getEmail(), e.getMessage());
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
            }
        }
        if (lastException != null) {
            logger.error("Giving up on sending OTP email to {} after {} attempts", user.getEmail(), MAX_RETRIES);
        }
    }

    public void sendSystemNotification(String subject, String content) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping system notification: {}", subject);
            return;
        }

        try {
            logger.info("Sending system notification: {}", subject);
            
            String fromEmail = resolveFromEmail();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send to admin
            message.setSubject("Print For You - " + subject);
            message.setText(content + "\n\nTimestamp: " + java.time.LocalDateTime.now());
            
            mailSender.send(message);
            logger.info("System notification sent successfully");
            
        } catch (Exception e) {
            logger.error("Failed to send system notification: {}", e.getMessage());
        }
    }
}
