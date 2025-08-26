package com.pdfprinting.controller;

import com.pdfprinting.model.User;
import com.pdfprinting.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

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
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("title", "Register - PDF Printing System");
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("user") User user,
                              BindingResult result,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("title", "Register - PDF Printing System");
            return "auth/register";
        }

        try {
            userService.registerUser(user);
            redirectAttributes.addFlashAttribute("message", 
                "Registration successful! Please check your email to verify your account.");
            return "redirect:/login";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("title", "Register - PDF Printing System");
            return "auth/register";
        }
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam("token") String token,
                             RedirectAttributes redirectAttributes) {
        if (userService.verifyEmail(token)) {
            redirectAttributes.addFlashAttribute("message", 
                "Email verified successfully! You can now login.");
        } else {
            redirectAttributes.addFlashAttribute("error", 
                "Invalid or expired verification token.");
        }
        return "redirect:/login";
    }

    @GetMapping("/verify")
    public String verifyPage(@RequestParam(value = "token", required = false) String token,
                            Model model) {
        model.addAttribute("title", "Verify Email - PDF Printing System");
        if (token != null) {
            model.addAttribute("token", token);
        }
        return "auth/verify";
    }

    @PostMapping("/verify")
    public String processVerification(@RequestParam("token") String token,
                                    RedirectAttributes redirectAttributes) {
        if (userService.verifyEmail(token)) {
            redirectAttributes.addFlashAttribute("message", 
                "Email verified successfully! You can now login.");
            return "redirect:/login";
        } else {
            redirectAttributes.addFlashAttribute("error", 
                "Invalid or expired verification token.");
            return "redirect:/verify";
        }
    }

    @GetMapping("/contact")
    public String contactPage(Model model) {
        model.addAttribute("title", "Contact Us - PDF Printing System");
        return "pages/contact";
    }

    @GetMapping("/terms")
    public String termsPage(Model model) {
        model.addAttribute("title", "Terms and Conditions - PDF Printing System");
        return "pages/terms";
    }
}
