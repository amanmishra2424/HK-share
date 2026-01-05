package com.pdfprinting.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.pdfprinting.model.User;
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Autowired(required = false)
    private TemplateEngine templateEngine;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${brevo.api.key:}")
    private String brevoApiKey;

    @Value("${app.mail.from-address:noreply@printforyou.com}")
    private String configuredFromEmail;

    @Value("${app.mail.from-name:Print For You}")
    private String fromName;

    @Value("${email.enabled:false}")
    private boolean emailEnabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public void logEmailConfiguration() {
        if (!logger.isInfoEnabled()) {
            return;
        }

        String fromAddress = resolveFromEmail();
        logger.info("Email configuration summary | enabled={} | brevoApiKeyConfigured={} | fromAddress={}",
                emailEnabled,
                StringUtils.hasText(brevoApiKey) && !isPlaceholder(brevoApiKey),
                maskEmail(fromAddress));
    }

    private Optional<String> validateEmailConfiguration() {
        if (!emailEnabled) {
            return Optional.of("EMAIL_ENABLED flag is false");
        }

        if (!StringUtils.hasText(brevoApiKey)) {
            return Optional.of("brevo.api.key / BREVO_API_KEY is empty");
        }

        if (isPlaceholder(brevoApiKey)) {
            return Optional.of("brevo.api.key contains unresolved placeholder");
        }

        String fromAddress = resolveFromEmail();
        if (!StringUtils.hasText(fromAddress)) {
            return Optional.of("No sender address configured (APP_MAIL_FROM_ADDRESS)");
        }

        if (isPlaceholder(fromAddress)) {
            return Optional.of("Sender address contains unresolved placeholder");
        }

        if ("your-email@gmail.com".equalsIgnoreCase(fromAddress) || "no-reply@example.com".equalsIgnoreCase(fromAddress)) {
            return Optional.of("Sender address is still set to the default example address");
        }

        return Optional.empty();
    }

    private boolean isPlaceholder(String value) {
        return value != null && value.contains("${");
    }

    private String maskEmail(String value) {
        if (!StringUtils.hasText(value)) {
            return "<empty>";
        }

        int atIndex = value.indexOf('@');
        if (atIndex <= 1) {
            return "****";
        }

        return value.charAt(0) + "***" + value.substring(atIndex);
    }

    private String resolveFromEmail() {
        if (StringUtils.hasText(configuredFromEmail) && !configuredFromEmail.contains("${")) {
            return configuredFromEmail.trim();
        }
        return "";
    }

    /**
     * Send email using Brevo REST API directly
     */
    private boolean sendEmailViaBrevo(String toEmail, String subject, String htmlContent, String textContent) {
        try {
            JSONObject payload = new JSONObject();
            
            // Set sender
            JSONObject sender = new JSONObject();
            sender.put("email", resolveFromEmail());
            sender.put("name", fromName);
            payload.put("sender", sender);
            
            // Set recipient
            JSONArray toArray = new JSONArray();
            JSONObject recipient = new JSONObject();
            recipient.put("email", toEmail);
            toArray.put(recipient);
            payload.put("to", toArray);
            
            // Set subject
            payload.put("subject", subject);
            
            // Set content
            if (StringUtils.hasText(htmlContent)) {
                payload.put("htmlContent", htmlContent);
            }
            if (StringUtils.hasText(textContent)) {
                payload.put("textContent", textContent);
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_API_URL))
                    .header("accept", "application/json")
                    .header("api-key", brevoApiKey)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Email sent successfully via Brevo to {} - response: {}", toEmail, response.body());
                return true;
            } else {
                logger.error("Brevo API error sending email to {}: {} - {}", toEmail, response.statusCode(), response.body());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error sending email via Brevo to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    public void sendVerificationEmail(User user) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping verification email for user: {}",
                    validationError.get(), user.getEmail());
            return;
        }

        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending verification email to {} (attempt {}/{})", user.getEmail(), attempt, MAX_RETRIES);
                
                String htmlContent = null;
                String textContent = null;
                
                if (templateEngine != null) {
                    Context context = new Context();
                    context.setVariable("user", user);
                    context.setVariable("verificationUrl", baseUrl + "/verify-email?token=" + user.getVerificationToken());
                    context.setVariable("baseUrl", baseUrl);
                    htmlContent = templateEngine.process("email/verification", context);
                } else {
                    textContent = "Welcome to Print For You!\n\n" +
                                "Please verify your email by clicking the following link:\n" +
                                baseUrl + "/verify-email?token=" + user.getVerificationToken() + "\n\n" +
                                "Thank you!";
                }
                
                boolean success = sendEmailViaBrevo(
                    user.getEmail(),
                    "Welcome to Print For You - Please Verify Your Email",
                    htmlContent,
                    textContent
                );
                
                if (success) {
                    logger.info("Verification email sent successfully to {}", user.getEmail());
                    return;
                }
                throw new RuntimeException("Brevo API returned failure");
                
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
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping batch notification for: {}",
                    validationError.get(), batchName);
            return;
        }

        try {
            logger.info("Sending batch processed notification for {} with {} files", batchName, fileCount);
            
            String htmlContent = null;
            String textContent = null;
            
            if (templateEngine != null) {
                Context context = new Context();
                context.setVariable("batchName", batchName);
                context.setVariable("fileCount", fileCount);
                context.setVariable("studentEmails", studentEmails);
                context.setVariable("processedAt", java.time.LocalDateTime.now());
                htmlContent = templateEngine.process("email/batch-processed", context);
            } else {
                textContent = "Batch Processed: " + batchName + "\n\n" +
                            "Files processed: " + fileCount + "\n" +
                            "Student emails: " + String.join(", ", studentEmails) + "\n" +
                            "Processed at: " + java.time.LocalDateTime.now();
            }
            
            String fromEmail = resolveFromEmail();
            boolean success = sendEmailViaBrevo(
                fromEmail, // Send to admin
                "Batch Processed: " + batchName + " (" + fileCount + " files)",
                htmlContent,
                textContent
            );
            
            if (success) {
                logger.info("Batch processed notification sent successfully for {}", batchName);
            }
            
        } catch (Exception e) {
            logger.error("Failed to send batch processed notification for {}: {}", batchName, e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping welcome email for user: {}",
                    validationError.get(), user.getEmail());
            return;
        }

        try {
            logger.info("Sending welcome email to {}", user.getEmail());
            
            String htmlContent = null;
            String textContent = null;
            
            if (templateEngine != null) {
                Context context = new Context();
                context.setVariable("user", user);
                context.setVariable("loginUrl", baseUrl + "/login");
                context.setVariable("baseUrl", baseUrl);
                htmlContent = templateEngine.process("email/welcome", context);
            } else {
                textContent = "Welcome to Print For You!\n\n" +
                            "Your account has been activated.\n\n" +
                            "Please log in by clicking the following link:\n" +
                            baseUrl + "/login\n\n" +
                            "Thank you!";
            }
            
            boolean success = sendEmailViaBrevo(
                user.getEmail(),
                "Welcome to Print For You - Account Activated!",
                htmlContent,
                textContent
            );
            
            if (success) {
                logger.info("Welcome email sent successfully to {}", user.getEmail());
            }
            
        } catch (Exception e) {
            logger.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping password reset email for user: {}",
                    validationError.get(), user.getEmail());
            return;
        }

        try {
            logger.info("Sending password reset email to {}", user.getEmail());
            
            String htmlContent = null;
            String textContent = null;
            
            if (templateEngine != null) {
                Context context = new Context();
                context.setVariable("user", user);
                context.setVariable("resetUrl", baseUrl + "/reset-password?token=" + resetToken);
                context.setVariable("baseUrl", baseUrl);
                htmlContent = templateEngine.process("email/password-reset", context);
            } else {
                textContent = "Hello " + user.getName() + ",\n\n" +
                            "You have requested to reset your password.\n\n" +
                            "Please click the following link to reset your password:\n" +
                            baseUrl + "/reset-password?token=" + resetToken + "\n\n" +
                            "Thank you!";
            }
            
            boolean success = sendEmailViaBrevo(
                user.getEmail(),
                "Print For You - Password Reset Request",
                htmlContent,
                textContent
            );
            
            if (success) {
                logger.info("Password reset email sent successfully to {}", user.getEmail());
            }
            
        } catch (Exception e) {
            logger.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public boolean testEmailConfiguration() {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Cannot test email configuration.", validationError.get());
            logEmailConfiguration();
            return false;
        }

        try {
            logger.info("Testing email configuration with Brevo API...");
            
            String fromEmail = resolveFromEmail();
            String textContent = "This is a test email to verify that the Brevo email configuration is working correctly.\n\n" +
                               "If you receive this email, the email system is properly configured.\n\n" +
                               "Timestamp: " + java.time.LocalDateTime.now();
            
            boolean success = sendEmailViaBrevo(
                fromEmail, // Send test email to self
                "Print For You - Email Configuration Test",
                null,
                textContent
            );
            
            if (success) {
                logger.info("Test email sent successfully via Brevo");
            }
            return success;
            
        } catch (Exception e) {
            logger.error("Email configuration test failed: {}", e.getMessage());
            return false;
        }
    }

    @Async
    public void sendOtpEmail(User user) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping OTP email for user: {}",
                    validationError.get(), user.getEmail());
            return;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending OTP email to {} via Brevo (attempt {}/{})", user.getEmail(), attempt, MAX_RETRIES);
                
                String htmlContent = "<html><body>" +
                    "<h2>Your OTP for Print For You Registration</h2>" +
                    "<p>Your One-Time Password (OTP) is:</p>" +
                    "<h1 style='color: #4CAF50; font-size: 32px; letter-spacing: 5px;'>" + user.getOtp() + "</h1>" +
                    "<p>This OTP is valid for <strong>10 minutes</strong>.</p>" +
                    "<p>If you did not request this OTP, please ignore this email.</p>" +
                    "<br><p>Thank you,<br>Print For You Team</p>" +
                    "</body></html>";
                
                String textContent = "Your OTP for Print For You Registration\n\n" +
                    "Your One-Time Password (OTP) is: " + user.getOtp() + "\n\n" +
                    "This OTP is valid for 10 minutes.\n\n" +
                    "If you did not request this OTP, please ignore this email.\n\n" +
                    "Thank you,\nPrint For You Team";
                
                boolean success = sendEmailViaBrevo(
                    user.getEmail(),
                    "Your OTP for Print For You Registration",
                    htmlContent,
                    textContent
                );
                
                if (success) {
                    logger.info("OTP email sent successfully to {} via Brevo", user.getEmail());
                    return;
                }
                throw new RuntimeException("Brevo API returned failure");
                
            } catch (Exception e) {
                lastException = e;
                logger.error("Failed to send OTP email to {}: {}", user.getEmail(), e.getMessage());
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        if (lastException != null) {
            logger.error("Giving up on sending OTP email to {} after {} attempts", user.getEmail(), MAX_RETRIES);
        }
    }

    /**
     * Send OTP email for pending registration (before user is created)
     */
    public boolean sendOtpEmailForRegistration(String email, String otp) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping OTP email for: {}",
                    validationError.get(), email);
            return false;
        }

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Sending registration OTP email to {} via Brevo (attempt {}/{})", email, attempt, MAX_RETRIES);
                
                String htmlContent = "<html><body>" +
                    "<h2>Your OTP for Print For You Registration</h2>" +
                    "<p>Your One-Time Password (OTP) is:</p>" +
                    "<h1 style='color: #4CAF50; font-size: 32px; letter-spacing: 5px;'>" + otp + "</h1>" +
                    "<p>This OTP is valid for <strong>10 minutes</strong>.</p>" +
                    "<p>If you did not request this OTP, please ignore this email.</p>" +
                    "<br><p>Thank you,<br>Print For You Team</p>" +
                    "</body></html>";
                
                String textContent = "Your OTP for Print For You Registration\n\n" +
                    "Your One-Time Password (OTP) is: " + otp + "\n\n" +
                    "This OTP is valid for 10 minutes.\n\n" +
                    "If you did not request this OTP, please ignore this email.\n\n" +
                    "Thank you,\nPrint For You Team";
                
                boolean success = sendEmailViaBrevo(
                    email,
                    "Your OTP for Print For You Registration",
                    htmlContent,
                    textContent
                );
                
                if (success) {
                    logger.info("Registration OTP email sent successfully to {} via Brevo", email);
                    return true;
                }
                throw new RuntimeException("Brevo API returned failure");
                
            } catch (Exception e) {
                lastException = e;
                logger.error("Failed to send registration OTP email to {}: {}", email, e.getMessage());
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        logger.error("Giving up on sending registration OTP email to {} after {} attempts", email, MAX_RETRIES);
        return false;
    }

    public void sendSystemNotification(String subject, String content) {
        Optional<String> validationError = validateEmailConfiguration();
        if (validationError.isPresent()) {
            logger.warn("Email not configured ({}). Skipping system notification: {}",
                    validationError.get(), subject);
            return;
        }

        try {
            logger.info("Sending system notification: {}", subject);
            
            String fromEmail = resolveFromEmail();
            String textContent = content + "\n\nTimestamp: " + java.time.LocalDateTime.now();
            
            boolean success = sendEmailViaBrevo(
                fromEmail, // Send to admin
                "Print For You - " + subject,
                null,
                textContent
            );
            
            if (success) {
                logger.info("System notification sent successfully via Brevo");
            }
            
        } catch (Exception e) {
            logger.error("Failed to send system notification: {}", e.getMessage());
        }
    }
}
