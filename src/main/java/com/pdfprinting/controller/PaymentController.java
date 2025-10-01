package com.pdfprinting.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.pdfprinting.config.RazorpayConfig;
import com.pdfprinting.model.User;
import com.pdfprinting.service.UserService;
import com.pdfprinting.service.WalletService;
import com.razorpay.RazorpayClient;
import com.razorpay.Order;
import org.json.JSONObject;

@Controller
@RequestMapping("/payment")
public class PaymentController {
    
    @Autowired
    private RazorpayConfig razorpayConfig;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private WalletService walletService;
    
    @PostMapping("/initiate")
    public String initiatePayment(@RequestParam("amount") BigDecimal walletAmount,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            System.out.println("Payment initiation started for amount: " + walletAmount);
            
            String email = authentication.getName();
            System.out.println("User email from authentication: " + email);
            
            User user = userService.findByEmail(email).orElse(null);
            
            if (user == null) {
                System.out.println("User not found for email: " + email);
                redirectAttributes.addFlashAttribute("error", "User not found");
                return "redirect:/student/wallet";
            }
            
            System.out.println("User found: " + user.getName());
        
        if (walletAmount.compareTo(BigDecimal.valueOf(10)) < 0 || 
            walletAmount.compareTo(BigDecimal.valueOf(10000)) > 0) {
            redirectAttributes.addFlashAttribute("error", 
                "Wallet amount must be between ₹10 and ₹10,000");
            return "redirect:/student/wallet";
        }
        
            // Calculate the total amount user needs to pay (including 2% service fee)
            System.out.println("Getting service fee percentage...");
            Double serviceFeePercentage = razorpayConfig.getService().getFee().getPercentage();
            System.out.println("Service fee percentage: " + serviceFeePercentage);
            
            BigDecimal serviceFeeMultiplier = BigDecimal.ONE.add(
                BigDecimal.valueOf(serviceFeePercentage).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            );
            BigDecimal totalPayableAmount = walletAmount.multiply(serviceFeeMultiplier).setScale(2, RoundingMode.HALF_UP);
            BigDecimal serviceFeeAmount = totalPayableAmount.subtract(walletAmount);
            
            System.out.println("Total payable amount: " + totalPayableAmount);
            
            // Convert amount to paise (Razorpay expects amount in smallest currency unit)
            long amountInPaise = totalPayableAmount.multiply(BigDecimal.valueOf(100)).longValue();
            
            // Create Razorpay order using SDK
            System.out.println("Creating Razorpay order...");
            RazorpayClient razorpayClient = new RazorpayClient(razorpayConfig.getKey().getId(), razorpayConfig.getKey().getSecret());
            
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", razorpayConfig.getCurrency());
            orderRequest.put("receipt", "receipt_" + System.currentTimeMillis() + "_" + user.getId());
            
            Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String orderId = razorpayOrder.get("id");
            System.out.println("Razorpay order created with ID: " + orderId);
            
            // Add to model for the payment form
            model.addAttribute("razorpayKeyId", razorpayConfig.getKey().getId());
            model.addAttribute("orderId", orderId);
            model.addAttribute("walletAmount", walletAmount);
            model.addAttribute("totalPayableAmount", totalPayableAmount);
            model.addAttribute("serviceFeeAmount", serviceFeeAmount);
            model.addAttribute("serviceFeePercentage", serviceFeePercentage);
            model.addAttribute("amountInPaise", amountInPaise);
            model.addAttribute("currency", razorpayConfig.getCurrency());
            model.addAttribute("callbackUrl", razorpayConfig.getCallbackUrl());
            model.addAttribute("userName", user.getName());
            model.addAttribute("userEmail", user.getEmail());
            model.addAttribute("userPhone", user.getPhoneNumber() != null ? user.getPhoneNumber() : "");
            model.addAttribute("userId", user.getId());
            
            return "payment/razorpay-form";
            
        } catch (Exception e) {
            System.out.println("Error in payment initiation: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", 
                "Payment initiation failed: " + e.getMessage());
            return "redirect:/student/wallet";
        }
    }
    
    @PostMapping("/callback")
    public String paymentCallback(@RequestParam("razorpay_payment_id") String paymentId,
                                 @RequestParam("razorpay_order_id") String orderId,
                                 @RequestParam("razorpay_signature") String signature,
                                 @RequestParam("wallet_amount") BigDecimal walletAmount,
                                 @RequestParam("user_id") Long userId,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            System.out.println("Payment callback received!");
            System.out.println("Payment ID: " + paymentId);
            System.out.println("Order ID: " + orderId);
            System.out.println("Signature: " + signature);
            System.out.println("Wallet Amount: " + walletAmount);
            System.out.println("User ID: " + userId);
            System.out.println("Payment callback received:");
            System.out.println("Payment ID: " + paymentId);
            System.out.println("Order ID: " + orderId);
            System.out.println("Signature: " + signature);
            System.out.println("Wallet Amount: " + walletAmount);
            System.out.println("User ID: " + userId);
            // Verify signature
            String data = orderId + "|" + paymentId;
            String expectedSignature = calculateHMACSHA256(data, razorpayConfig.getKey().getSecret());
            
            System.out.println("Signature verification:");
            System.out.println("Data: " + data);
            System.out.println("Expected signature: " + expectedSignature);
            System.out.println("Received signature: " + signature);
            System.out.println("Signatures match: " + signature.equals(expectedSignature));
            
            // Handle test signatures (from mock payment)
            boolean isTestPayment = signature.startsWith("test_signature_");
            boolean signatureValid = signature.equals(expectedSignature) || isTestPayment;
            
            if (signatureValid) {
                if (isTestPayment) {
                    System.out.println("Test payment detected - bypassing signature verification");
                }
                // Payment verified successfully
                User user = userService.findById(userId).orElse(null);
                
                if (user != null) {
                    System.out.println("Adding money to wallet for user: " + user.getName());
                    // Add the requested amount to wallet (not the total paid amount)
                    walletService.addMoney(user, walletAmount, "RAZORPAY_PAYMENT", 
                        "Razorpay payment - Order: " + orderId + ", Payment: " + paymentId);
                    
                    System.out.println("Money added successfully to wallet!");
                    redirectAttributes.addFlashAttribute("message", 
                        "Payment successful! ₹" + walletAmount + " added to your wallet.");
                    // Redirect to student dashboard on success
                    return "redirect:/student/dashboard";
                } else {
                    System.out.println("User not found with ID: " + userId);
                    redirectAttributes.addFlashAttribute("error", "User not found");
                }
            } else {
                System.out.println("Signature verification failed!");
                redirectAttributes.addFlashAttribute("error", 
                    "Payment verification failed. Invalid signature.");
            }
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", 
                "Payment processing failed: " + e.getMessage());
        }
        
        // On failure or missing user, go back to wallet page
        return "redirect:/student/wallet";
    }
    
    @PostMapping("/failed")
    public String paymentFailed(@RequestParam(value = "error_description", required = false) String errorDescription,
                               RedirectAttributes redirectAttributes) {
        
        System.out.println("Payment failed callback received:");
        System.out.println("Error description: " + errorDescription);
        
        redirectAttributes.addFlashAttribute("error", 
            "Payment failed: " + (errorDescription != null ? errorDescription : "Unknown error"));
        return "redirect:/student/wallet";
    }
    
    private String calculateHMACSHA256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes());
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}