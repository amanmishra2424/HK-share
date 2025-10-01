package com.pdfprinting.controller;

import com.pdfprinting.model.User;
import com.pdfprinting.repository.UserRepository;
import com.pdfprinting.security.JwtUtil;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
public class AdminLoginDebugController {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * Debug endpoint to check admin user details and password
     * Usage: http://localhost:8082/debug-admin?email=mishraaman2424@gmail.com&password=admin123
     */
    @GetMapping("/debug-admin")
    @ResponseBody
    public String debugAdmin(@RequestParam("email") String email, 
                            @RequestParam("password") String password) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                return "❌ User not found: " + email;
            }
            
            User user = userOpt.get();
            StringBuilder debug = new StringBuilder();
            debug.append("🔍 Admin Debug Info:\n");
            debug.append("Email: ").append(user.getEmail()).append("\n");
            debug.append("Name: ").append(user.getName()).append("\n");
            debug.append("Role: ").append(user.getRole()).append("\n");
            debug.append("Email Verified: ").append(user.isEmailVerified()).append("\n");
            debug.append("Password Hash: ").append(user.getPassword().substring(0, 20)).append("...\n");
            
            // Test password matching
            boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
            debug.append("Password Matches: ").append(passwordMatches ? "✅" : "❌").append("\n");
            
            if (!passwordMatches) {
                debug.append("\n💡 Fixing password...\n");
                // Force update password
                user.setPassword(passwordEncoder.encode(password));
                user.setEmailVerified(true); // Ensure email is verified
                userRepository.save(user);
                debug.append("✅ Password updated and email verified!\n");
            }
            
            return debug.toString().replace("\n", "<br>");
            
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }
    
    /**
     * Emergency admin login bypass
     * Usage: http://localhost:8082/admin-emergency-login?email=mishraaman2424@gmail.com&password=admin123
     */
    @GetMapping("/admin-emergency-login")
    public String emergencyAdminLogin(@RequestParam("email") String email,
                                     @RequestParam("password") String password,
                                     HttpServletResponse response,
                                     jakarta.servlet.http.HttpServletRequest request) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (!userOpt.isPresent()) {
                return "redirect:/login?error=User not found";
            }
            
            User user = userOpt.get();
            
            // Force password reset and verification
            user.setPassword(passwordEncoder.encode(password));
            user.setEmailVerified(true);
            userRepository.save(user);
            
            // Create JWT token
            String token = jwtUtil.generateToken(email, user.getRole().name());
            
            // Set cookie
            long maxAge = 86400L;
            long expiryMs = Instant.now().toEpochMilli() + (maxAge * 1000L);
            String expires = ZonedDateTime.ofInstant(Instant.ofEpochMilli(expiryMs), ZoneId.of("GMT"))
                    .format(DateTimeFormatter.RFC_1123_DATE_TIME);
            StringBuilder sb = new StringBuilder();
            sb.append("JWT=").append(token)
                .append("; Path=/; HttpOnly; Max-Age=").append(maxAge)
                .append("; Expires=").append(expires)
                .append("; SameSite=Lax");
            if (request.isSecure()) {
                sb.append("; Secure");
            }
            response.addHeader("Set-Cookie", sb.toString());
            
            return "redirect:/admin/dashboard";
        } catch (Exception e) {
            return "redirect:/login?error=" + e.getMessage();
        }
    }
}