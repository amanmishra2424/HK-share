package com.pdfprinting.service;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Service
public class GitHubStorageService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubStorageService.class);
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    @Value("${github.token}")
    private String githubToken;

    @Value("${github.repository}")
    private String repositoryName;

    private GitHub getGitHub() throws IOException {
        if (githubToken == null || githubToken.trim().isEmpty() || githubToken.equals("your-github-token")) {
            throw new IOException("GitHub token is not configured. Please set GITHUB_TOKEN environment variable.");
        }
        
        if (repositoryName == null || repositoryName.trim().isEmpty() || repositoryName.equals("username/repository-name")) {
            throw new IOException("GitHub repository is not configured. Please set GITHUB_REPOSITORY environment variable.");
        }
        
        return new GitHubBuilder().withOAuthToken(githubToken).build();
    }

    public String uploadFile(MultipartFile file, String filename, String batch) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Attempting to upload file {} to GitHub (attempt {}/{})", filename, attempt, MAX_RETRIES);
                
                GitHub github = getGitHub();
                GHRepository repository = github.getRepository(repositoryName);
                
                // Create path: uploads/batch/filename
                String path = "uploads/" + sanitizeBatchName(batch) + "/" + filename;
                
                // Convert file to base64
                byte[] fileContent = file.getBytes();
                String base64Content = Base64.getEncoder().encodeToString(fileContent);
                
                // Check if file already exists
                try {
                    repository.getFileContent(path);
                    // File exists, create unique name
                    String baseName = filename.substring(0, filename.lastIndexOf('.'));
                    String extension = filename.substring(filename.lastIndexOf('.'));
                    path = "uploads/" + sanitizeBatchName(batch) + "/" + baseName + "_" + System.currentTimeMillis() + extension;
                    logger.info("File exists, using unique path: {}", path);
                } catch (Exception e) {
                    // File doesn't exist, continue with original path
                }
                
                // Upload to GitHub
                repository.createContent()
                    .content(base64Content)
                    .path(path)
                    .message("Upload PDF: " + file.getOriginalFilename() + " from " + batch)
                    .commit();
                
                logger.info("Successfully uploaded file {} to GitHub at path {}", filename, path);
                return path;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Upload attempt {}/{} failed for file {}: {}", attempt, MAX_RETRIES, filename, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Upload interrupted", ie);
                    }
                }
            }
        }
        
        throw new Exception("Failed to upload file to GitHub after " + MAX_RETRIES + " attempts: " + 
                          (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    public void deleteFile(String path) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Attempting to delete file {} from GitHub (attempt {}/{})", path, attempt, MAX_RETRIES);
                
                GitHub github = getGitHub();
                GHRepository repository = github.getRepository(repositoryName);
                
                // Get file content to get SHA
                var content = repository.getFileContent(path);
                
                // Delete file
                repository.createContent()
                    .content("")
                    .path(path)
                    .sha(content.getSha())
                    .message("Delete PDF: " + path)
                    .commit();
                
                logger.info("Successfully deleted file {} from GitHub", path);
                return;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Delete attempt {}/{} failed for file {}: {}", attempt, MAX_RETRIES, path, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Delete interrupted", ie);
                    }
                }
            }
        }
        
        throw new Exception("Failed to delete file from GitHub after " + MAX_RETRIES + " attempts: " + 
                          (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    public byte[] downloadFile(String path) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                logger.info("Attempting to download file {} from GitHub (attempt {}/{})", path, attempt, MAX_RETRIES);
                
                GitHub github = getGitHub();
                GHRepository repository = github.getRepository(repositoryName);
                
                var content = repository.getFileContent(path);
                byte[] fileBytes = Base64.getDecoder().decode(content.getContent());
                
                logger.info("Successfully downloaded file {} from GitHub ({} bytes)", path, fileBytes.length);
                return fileBytes;
                
            } catch (Exception e) {
                lastException = e;
                logger.warn("Download attempt {}/{} failed for file {}: {}", attempt, MAX_RETRIES, path, e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Download interrupted", ie);
                    }
                }
            }
        }
        
        throw new Exception("Failed to download file from GitHub after " + MAX_RETRIES + " attempts: " + 
                          (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    public List<String> listBatchFiles(String batch) throws Exception {
        try {
            logger.info("Listing files for batch: {}", batch);
            
            GitHub github = getGitHub();
            GHRepository repository = github.getRepository(repositoryName);
            
            String batchPath = "uploads/" + sanitizeBatchName(batch);
            
            try {
                var contents = repository.getDirectoryContent(batchPath);
                List<String> filePaths = contents.stream()
                    .filter(content -> content.isFile())
                    .map(content -> content.getPath())
                    .toList();
                
                logger.info("Found {} files in batch {}", filePaths.size(), batch);
                return filePaths;
                
            } catch (Exception e) {
                // Directory doesn't exist or is empty
                logger.info("No files found for batch {} (directory may not exist)", batch);
                return List.of();
            }
            
        } catch (Exception e) {
            throw new Exception("Failed to list files for batch " + batch + ": " + e.getMessage());
        }
    }

    public boolean testConnection() {
        try {
            GitHub github = getGitHub();
            GHRepository repository = github.getRepository(repositoryName);
            
            // Try to access repository info
            String repoName = repository.getName();
            String ownerLogin = repository.getOwner().getLogin();
            
            logger.info("Successfully connected to GitHub repository: {}/{}", ownerLogin, repoName);
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to connect to GitHub repository: {}", e.getMessage());
            return false;
        }
    }

    public void initializeRepository() throws Exception {
        try {
            logger.info("Initializing GitHub repository structure");
            
            GitHub github = getGitHub();
            GHRepository repository = github.getRepository(repositoryName);
            
            // Create uploads directory with README
            String readmeContent = "# Print For You - File Storage\n\n" +
                                 "This directory contains uploaded PDF files organized by batch.\n\n" +
                                 "## Structure\n" +
                                 "- `uploads/batch-1/` - Files from Batch 1\n" +
                                 "- `uploads/batch-2/` - Files from Batch 2\n" +
                                 "- etc.\n\n" +
                                 "Files are automatically managed by the Print For You.";
            
            try {
                repository.createContent()
                    .content(Base64.getEncoder().encodeToString(readmeContent.getBytes()))
                    .path("uploads/README.md")
                    .message("Initialize Print For You file storage")
                    .commit();
                
                logger.info("Successfully initialized repository structure");
                
            } catch (Exception e) {
                if (e.getMessage().contains("already exists")) {
                    logger.info("Repository structure already exists");
                } else {
                    throw e;
                }
            }
            
        } catch (Exception e) {
            throw new Exception("Failed to initialize repository: " + e.getMessage());
        }
    }

    private String sanitizeBatchName(String batch) {
        return batch.toLowerCase()
                   .replace(" ", "-")
                   .replaceAll("[^a-z0-9-]", "")
                   .replaceAll("-+", "-")
                   .replaceAll("^-|-$", "");
    }

    public String getRepositoryInfo() {
        try {
            GitHub github = getGitHub();
            GHRepository repository = github.getRepository(repositoryName);
            
            return String.format("Repository: %s/%s | Private: %s | Size: %d KB", 
                repository.getOwner().getLogin(),
                repository.getName(),
                repository.isPrivate(),
                repository.getSize());
                
        } catch (Exception e) {
            return "Repository information unavailable: " + e.getMessage();
        }
    }
}
