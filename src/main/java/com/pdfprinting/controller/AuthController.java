package com.pdfprinting.controller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.pdfprinting.model.User;
import com.pdfprinting.security.JwtUtil;
import com.pdfprinting.service.CustomUserDetailsService;
import com.pdfprinting.service.UserService;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "logout", required = false) String logout,
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "Invalid email or password. Please make sure your email is verified.");
        }
        if (logout != null) {
            model.addAttribute("message", "You have been logged out successfully.");
        }
        model.addAttribute("title", "Login - PDF Printing System");
        return "auth/login";
    }

    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, Model model) {
        try {
            userService.registerUser(user);
            // Redirect to dedicated OTP page after registration
            return "redirect:/verify-otp?email=" + user.getEmail();
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/verify-otp")
    public String showOtpPage(@RequestParam(value = "email", required = false) String email,
                              @RequestParam(value = "message", required = false) String message,
                              @RequestParam(value = "otpError", required = false) String otpError,
                              Model model) {
        if (email != null) model.addAttribute("email", email);
        if (message != null) model.addAttribute("message", message);
        if (otpError != null) model.addAttribute("error", otpError);
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam("email") String email, @RequestParam("otp") String otp, Model model, HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        boolean verified = userService.verifyOtp(email, otp == null ? "" : otp.trim());
        if (verified) {
            // Issue JWT cookie for client
            try {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
                String role = userDetails.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("STUDENT");
                String token = jwtUtil.generateToken(email, role);
                                // Build a single Set-Cookie header with Expires and SameSite for reliable browser persistence
                                long maxAge = 86400L;
                                long expiryMs = Instant.now().toEpochMilli() + (maxAge * 1000L);
                                String expires = ZonedDateTime.ofInstant(Instant.ofEpochMilli(expiryMs), ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
                                StringBuilder sb = new StringBuilder();
                                sb.append("JWT=").append(token)
                                    .append("; Path=/; HttpOnly; Max-Age=").append(maxAge)
                                    .append("; Expires=").append(expires)
                                    .append("; SameSite=Lax");
                                if (request.isSecure()) {
                                        sb.append("; Secure");
                                }
                                response.addHeader("Set-Cookie", sb.toString());
            } catch (Exception ex) {
                // ignore token issuance failure; user can still login manually
            }
            // Redirect to dashboard after successful verification and auto-login
            return "redirect:/dashboard";
        } else {
            model.addAttribute("email", email);
            model.addAttribute("error", "Invalid or expired OTP. Please try again.");
            return "auth/verify-otp";
        }
    }

    @PostMapping("/login")
    public String handleLogin(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              HttpServletResponse response,
                              Model model,
                              jakarta.servlet.http.HttpServletRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            String role = userDetails.getAuthorities().stream().findFirst().map(a -> a.getAuthority().replace("ROLE_", "")).orElse("STUDENT");
            String token = jwtUtil.generateToken(username, role);

                        long maxAge = 86400L;
                        long expiryMs = Instant.now().toEpochMilli() + (maxAge * 1000L);
                        String expires = ZonedDateTime.ofInstant(Instant.ofEpochMilli(expiryMs), ZoneId.of("GMT")).format(DateTimeFormatter.RFC_1123_DATE_TIME);
                        StringBuilder sb = new StringBuilder();
                        sb.append("JWT=").append(token)
                            .append("; Path=/; HttpOnly; Max-Age=").append(maxAge)
                            .append("; Expires=").append(expires)
                            .append("; SameSite=Lax");
                        if (request.isSecure()) {
                                sb.append("; Secure");
                        }
                        response.addHeader("Set-Cookie", sb.toString());

            return "redirect:/dashboard";
        } catch (Exception e) {
            model.addAttribute("error", "Invalid email or password. Please make sure your email is verified.");
            return "auth/login";
        }
    }

    @PostMapping("/resend-otp")
    public String resendOtp(@RequestParam("email") String email, Model model) {
        try {
            userService.resendOtp(email);
            model.addAttribute("email", email);
            model.addAttribute("message", "OTP resent successfully");
            return "auth/verify-otp";
        } catch (Exception e) {
            model.addAttribute("email", email);
            model.addAttribute("error", e.getMessage());
            return "auth/verify-otp";
        }
    }

    // Temporary debug endpoint - remove in production
    @GetMapping("/debug-user")
    @ResponseBody
    public String debugUser(@RequestParam("email") String email) {
        return userService.findByEmail(email)
                .map(u -> "email=" + u.getEmail() + ", otp=" + u.getOtp() + ", otpExpiry=" + u.getOtpExpiry() + ", verified=" + u.isEmailVerified())
                .orElse("user not found");
    }
}
