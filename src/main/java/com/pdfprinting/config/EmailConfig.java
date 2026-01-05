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
        logger.info("Testing Brevo email configuration...");
        emailService.logEmailConfiguration();
        
        if (emailService.testEmailConfiguration()) {
            logger.info("Brevo email system is configured and working correctly");
        } else {
            logger.error("Brevo email configuration test failed. Please check your settings:");
            logger.error("1. Set BREVO_API_KEY environment variable with your Brevo API key");
            logger.error("2. Configure APP_MAIL_FROM_ADDRESS with a verified sender email in Brevo");
            logger.error("3. Ensure email.enabled=true in application.properties");
            logger.error("Get your API key from: https://app.brevo.com/settings/keys/api");
        }
    }
}
