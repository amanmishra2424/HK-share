package com.pdfprinting.controller;

import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        
        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            model.addAttribute("errorCode", statusCode);
            
            if (statusCode == 404) {
                model.addAttribute("errorTitle", "Page Not Found");
                model.addAttribute("errorMessage", "The page you're looking for doesn't exist.");
            } else if (statusCode == 500) {
                model.addAttribute("errorTitle", "Internal Server Error");
                model.addAttribute("errorMessage", "Something went wrong on our end.");
                if (exception != null) {
                    model.addAttribute("errorDetails", exception.toString());
                }
                if (message != null) {
                    model.addAttribute("errorMessage", message.toString());
                }
            } else {
                model.addAttribute("errorTitle", "Error " + statusCode);
                model.addAttribute("errorMessage", message != null ? message.toString() : "An error occurred");
            }
        }
        
        return "error";
    }
}