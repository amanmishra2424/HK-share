package com.pdfprinting.controller;

import com.pdfprinting.service.GitHubStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/github")
@PreAuthorize("hasRole('ADMIN')")
public class GitHubTestController {

    @Autowired
    private GitHubStorageService gitHubStorageService;

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testGitHubConnection() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean connected = gitHubStorageService.testConnection();
            response.put("connected", connected);
            
            if (connected) {
                response.put("repositoryInfo", gitHubStorageService.getRepositoryInfo());
                response.put("message", "GitHub integration is working correctly");
            } else {
                response.put("message", "GitHub connection failed. Check configuration.");
            }
            
        } catch (Exception e) {
            response.put("connected", false);
            response.put("error", e.getMessage());
            response.put("message", "GitHub integration error");
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/initialize")
    public ResponseEntity<Map<String, Object>> initializeRepository() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            gitHubStorageService.initializeRepository();
            response.put("success", true);
            response.put("message", "Repository initialized successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Failed to initialize repository");
        }
        
        return ResponseEntity.ok(response);
    }
}
