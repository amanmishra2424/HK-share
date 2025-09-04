package com.pdfprinting.controller;

import com.pdfprinting.model.User;
import com.pdfprinting.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import com.pdfprinting.service.CustomUserDetailsService;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

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
    public String verifyOtp(@RequestParam("email") String email, @RequestParam("otp") String otp, Model model) {
        boolean verified = userService.verifyOtp(email, otp == null ? "" : otp.trim());
        if (verified) {
            // Auto-login the user
            try {
                UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ex) {
                // ignore auth failure; user can still login manually
            }
            // Redirect to dashboard after successful verification and auto-login
            return "redirect:/dashboard";
        } else {
            model.addAttribute("email", email);
            model.addAttribute("error", "Invalid or expired OTP. Please try again.");
            return "auth/verify-otp";
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
