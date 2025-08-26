package com.pdfprinting.config;

import com.pdfprinting.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3) // Run after DataInitializer and GitHubConfig
public class EmailConfig implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(EmailConfig.class);

    @Autowired
    private EmailService emailService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Testing email configuration...");
        
        if (emailService.testEmailConfiguration()) {
            logger.info("Email system is configured and working correctly");
        } else {
            logger.error("Email configuration test failed. Please check your email settings:");
            logger.error("1. Set EMAIL_USERNAME environment variable with your email address");
            logger.error("2. Set EMAIL_PASSWORD environment variable with your app password");
            logger.error("3. Ensure SMTP settings are correct in application.properties");
            logger.error("4. For Gmail, enable 2-factor authentication and use an app password");
        }
    }
}
