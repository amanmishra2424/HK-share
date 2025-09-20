package com.pdfprinting.controller;

import com.pdfprinting.config.PaytmConfig;
import com.pdfprinting.model.User;
import com.pdfprinting.service.UserService;
import com.pdfprinting.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

@Controller
@RequestMapping("/payment")
public class PaymentController {
    
    @Autowired
    private PaytmConfig paytmConfig;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private WalletService walletService;
    
    @PostMapping("/initiate")
    public String initiatePayment(@RequestParam("amount") BigDecimal amount,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        
        String email = authentication.getName();
        User user = userService.findByEmail(email).orElse(null);
        
        if (user == null) {
            redirectAttributes.addFlashAttribute("error", "User not found");
            return "redirect:/student/wallet";
        }
        
        if (amount.compareTo(BigDecimal.valueOf(10)) < 0 || 
            amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            redirectAttributes.addFlashAttribute("error", 
                "Amount must be between ₹10 and ₹10,000");
            return "redirect:/student/wallet";
        }
        
        try {
            // Generate unique order ID
            String orderId = "ORDER_" + System.currentTimeMillis() + "_" + user.getId();
            String custId = "CUST_" + user.getId();
            
            // Create parameters for Paytm
            Map<String, String> paytmParams = new TreeMap<>();
            paytmParams.put("MID", paytmConfig.getMerchant().getId());
            paytmParams.put("WEBSITE", paytmConfig.getWebsite());
            paytmParams.put("INDUSTRY_TYPE_ID", paytmConfig.getIndustry().getType());
            paytmParams.put("CHANNEL_ID", paytmConfig.getChannel().getId());
            paytmParams.put("ORDER_ID", orderId);
            paytmParams.put("CUST_ID", custId);
            paytmParams.put("TXN_AMOUNT", amount.toString());
            paytmParams.put("CALLBACK_URL", paytmConfig.getCallbackUrl());
            
            // Generate checksum
            String checksum = generateChecksum(paytmParams, paytmConfig.getMerchant().getKey());
            paytmParams.put("CHECKSUMHASH", checksum);
            
            // Add to model for form submission
            model.addAttribute("paytmParams", paytmParams);
            model.addAttribute("paytmUrl", paytmConfig.getPaymentUrl());
            model.addAttribute("amount", amount);
            model.addAttribute("orderId", orderId);
            
            return "payment/paytm-form";
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Payment initiation failed: " + e.getMessage());
            return "redirect:/student/wallet";
        }
    }
    
    @PostMapping("/callback")
    public String paymentCallback(@RequestParam Map<String, String> params,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            String receivedChecksum = params.get("CHECKSUMHASH");
            params.remove("CHECKSUMHASH");
            
            // Verify checksum
            boolean isValidChecksum = verifyChecksum(params, paytmConfig.getMerchant().getKey(), receivedChecksum);
            
            if (isValidChecksum) {
                String status = params.get("STATUS");
                String orderId = params.get("ORDERID");
                String txnAmount = params.get("TXNAMOUNT");
                String custId = params.get("CUSTID");
                
                if ("TXN_SUCCESS".equals(status)) {
                    // Extract user ID from customer ID
                    Long userId = Long.parseLong(custId.replace("CUST_", ""));
                    User user = userService.findById(userId).orElse(null);
                    
                    if (user != null) {
                        BigDecimal amount = new BigDecimal(txnAmount);
                        walletService.addMoney(user, amount, "PAYTM_PAYMENT", "Paytm payment - Order: " + orderId);
                        
                        redirectAttributes.addFlashAttribute("message", 
                            "Payment successful! ₹" + amount + " added to your wallet.");
                    } else {
                        redirectAttributes.addFlashAttribute("error", "User not found");
                    }
                } else {
                    redirectAttributes.addFlashAttribute("error", 
                        "Payment failed: " + params.get("RESPMSG"));
                }
            } else {
                redirectAttributes.addFlashAttribute("error", 
                    "Payment verification failed. Invalid checksum.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Payment processing failed: " + e.getMessage());
        }
        
        return "redirect:/student/wallet";
    }
    
    private String generateChecksum(Map<String, String> params, String key) throws Exception {
        StringBuilder allFields = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                allFields.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }
        String allFieldsStr = allFields.toString();
        if (allFieldsStr.endsWith("&")) {
            allFieldsStr = allFieldsStr.substring(0, allFieldsStr.length() - 1);
        }
        
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(allFieldsStr.getBytes());
        
        return Base64.getEncoder().encodeToString(hash);
    }
    
    private boolean verifyChecksum(Map<String, String> params, String key, String checksum) throws Exception {
        String generatedChecksum = generateChecksum(params, key);
        return generatedChecksum.equals(checksum);
    }
}