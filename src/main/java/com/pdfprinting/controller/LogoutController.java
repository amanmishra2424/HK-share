package com.pdfprinting.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LogoutController {

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
    // Clear cookie via Set-Cookie header so SameSite and Path are correctly unset on client
    StringBuilder sb = new StringBuilder();
    sb.append("JWT=; Path=/; HttpOnly; Max-Age=0; SameSite=Lax");
    // Do not append Secure flag here; browsers will clear cookie regardless of Secure when Max-Age=0
    response.addHeader("Set-Cookie", sb.toString());
        return "redirect:/login?logout=true";
    }
}
