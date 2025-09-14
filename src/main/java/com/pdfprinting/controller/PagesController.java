package com.pdfprinting.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PagesController {

    @GetMapping("/terms")
    public String terms(Model model) {
        model.addAttribute("title", "Terms and Conditions - Print For You");
        return "pages/terms";
    }

    @GetMapping("/contact")
    public String contact(Model model) {
        model.addAttribute("title", "Contact Us - Print For You");
        return "pages/contact";
    }
}
