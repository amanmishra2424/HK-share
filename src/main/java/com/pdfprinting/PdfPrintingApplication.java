package com.pdfprinting;

import com.pdfprinting.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PdfPrintingApplication implements CommandLineRunner {

    @Autowired
    private UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(PdfPrintingApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        userService.initializeAdmin();
    }
}
