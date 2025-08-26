package com.pdfprinting.service;

import com.pdfprinting.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

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

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    private boolean isEmailConfigured() {
        return emailEnabled && mailSender != null && 
               !fromEmail.equals("your-email@gmail.com") && 
               !fromEmail.contains("${EMAIL_USERNAME");
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
                
                helper.setFrom(fromEmail, "PDF Printing System");
                helper.setTo(user.getEmail());
                helper.setSubject("Welcome to PDF Printing System - Please Verify Your Email");
                
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
                    String textContent = "Welcome to PDF Printing System!\n\n" +
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
            
            helper.setFrom(fromEmail, "PDF Printing System");
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
            
            helper.setFrom(fromEmail, "PDF Printing System");
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to PDF Printing System - Account Activated!");
            
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
                String textContent = "Welcome to PDF Printing System!\n\n" +
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
            
            helper.setFrom(fromEmail, "PDF Printing System");
            helper.setTo(user.getEmail());
            helper.setSubject("PDF Printing System - Password Reset Request");
            
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
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send test email to self
            message.setSubject("PDF Printing System - Email Configuration Test");
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

    public void sendSystemNotification(String subject, String content) {
        if (!isEmailConfigured()) {
            logger.warn("Email not configured. Skipping system notification: {}", subject);
            return;
        }

        try {
            logger.info("Sending system notification: {}", subject);
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(fromEmail); // Send to admin
            message.setSubject("PDF Printing System - " + subject);
            message.setText(content + "\n\nTimestamp: " + java.time.LocalDateTime.now());
            
            mailSender.send(message);
            logger.info("System notification sent successfully");
            
        } catch (Exception e) {
            logger.error("Failed to send system notification: {}", e.getMessage());
        }
    }
}
