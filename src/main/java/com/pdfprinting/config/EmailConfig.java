package com.pdfprinting.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.pdfprinting.service.EmailService;

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
            logger.error("Email configuration test failed. Please check your Brevo SMTP settings:");
            logger.error("1. Set BREVO_SMTP_HOST (default smtp-relay.brevo.com) and BREVO_SMTP_PORT (default 587)");
            logger.error("2. Provide BREVO_SMTP_USERNAME (your Brevo SMTP login) and BREVO_SMTP_PASSWORD (SMTP key)");
            logger.error("3. Configure APP_MAIL_FROM_ADDRESS with a verified sender email in Brevo");
            logger.error("4. Ensure email.enabled=true and TLS is allowed on your hosting provider (Render)");
        }
    }
}
