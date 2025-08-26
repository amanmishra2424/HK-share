package com.pdfprinting.controller;

import com.pdfprinting.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@PreAuthorize("hasRole('ADMIN')")
public class EmailTestController {

    @Autowired
    private EmailService emailService;

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEmailConfiguration() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean working = emailService.testEmailConfiguration();
            response.put("working", working);
            
            if (working) {
                response.put("message", "Email system is working correctly");
            } else {
                response.put("message", "Email configuration test failed");
            }
            
        } catch (Exception e) {
            response.put("working", false);
            response.put("error", e.getMessage());
            response.put("message", "Email system error");
        }
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/send-test")
    public ResponseEntity<Map<String, Object>> sendTestNotification() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            emailService.sendSystemNotification("Test Notification", 
                "This is a test notification to verify the email system is working correctly.");
            
            response.put("success", true);
            response.put("message", "Test notification sent successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Failed to send test notification");
        }
        
        return ResponseEntity.ok(response);
    }
}
