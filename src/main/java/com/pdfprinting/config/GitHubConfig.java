package com.pdfprinting.config;

import com.pdfprinting.service.GitHubStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2) // Run after DataInitializer
public class GitHubConfig implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(GitHubConfig.class);

    @Autowired
    private GitHubStorageService gitHubStorageService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing GitHub integration...");
        
        // Test GitHub connection
        if (gitHubStorageService.testConnection()) {
            logger.info("GitHub connection successful");
            logger.info("Repository info: {}", gitHubStorageService.getRepositoryInfo());
            
            // Initialize repository structure
            try {
                gitHubStorageService.initializeRepository();
                logger.info("GitHub repository initialized successfully");
            } catch (Exception e) {
                logger.warn("Failed to initialize repository structure: {}", e.getMessage());
            }
            
        } else {
            logger.error("GitHub connection failed. Please check your configuration:");
            logger.error("1. Set GITHUB_TOKEN environment variable with a valid GitHub personal access token");
            logger.error("2. Set GITHUB_REPOSITORY environment variable (format: username/repository-name)");
            logger.error("3. Ensure the repository exists and the token has appropriate permissions");
        }
    }
}
