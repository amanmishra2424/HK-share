package com.pdfprinting.controller;

import com.pdfprinting.model.User;
import com.pdfprinting.repository.UserRepository;
import com.pdfprinting.repository.PdfUploadRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Controller
@RequestMapping("/admin/debug")
public class AdminDebugController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PdfUploadRepository pdfUploadRepository;

    @Value("${admin.purge.secret:purge-secret}")
    private String purgeSecret;

    // Simple debug endpoint to list users by roll number
    @GetMapping("/users/by-roll/{roll}")
    @ResponseBody
    public List<User> getUsersByRoll(@PathVariable("roll") String roll) {
        return userRepository.findAll().stream()
                .filter(u -> roll.equals(u.getRollNumber()))
                .toList();
    }

    // Debug endpoint to inspect indexes on users table
    @GetMapping("/users/indexes")
    @ResponseBody
    public List<java.util.Map<String,Object>> getUserTableIndexes() {
        // This runs SHOW INDEX which works on MySQL/MariaDB. Returns rows describing indexes.
        return jdbcTemplate.queryForList("SHOW INDEX FROM users");
    }

    // Report duplicate groups (branch, division, roll_number) with counts > 1
    @GetMapping("/duplicates")
    @ResponseBody
    public List<java.util.Map<String,Object>> getDuplicateGroups() {
        String sql = "SELECT branch, division, roll_number as rollNumber, COUNT(*) as cnt FROM users GROUP BY branch, division, roll_number HAVING COUNT(*) > 1";
        return jdbcTemplate.queryForList(sql);
    }

    // Admin purge endpoint: deletes all pdf uploads and non-admin users. Requires secret.
    @PostMapping("/purge")
    @ResponseBody
    public String purgeData(@RequestParam("secret") String secret) {
        if (secret == null || !secret.equals(purgeSecret)) {
            return "Invalid secret";
        }
        // delete PDF uploads first to avoid FK issues
        pdfUploadRepository.deleteAll();
        // delete non-admin users
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != com.pdfprinting.model.User.Role.ADMIN)
                .forEach(u -> userRepository.deleteById(u.getId()));
        return "Purge completed";
    }
}
