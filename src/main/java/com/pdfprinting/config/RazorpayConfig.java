package com.pdfprinting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayConfig {
    
    private Key key = new Key();
    private String currency = "INR";
    private String callbackUrl;
    private String webhookSecret;
    private Service service = new Service();
    
    // Getters and Setters
    public Key getKey() { return key; }
    public void setKey(Key key) { this.key = key; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    
    // Inner classes
    public static class Key {
        private String id;
        private String secret;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
    }
    
    public static class Service {
        private Fee fee = new Fee();
        
        public Fee getFee() { return fee; }
        public void setFee(Fee fee) { this.fee = fee; }
        
        public static class Fee {
            private Double percentage = 2.0;
            
            public Double getPercentage() { return percentage; }
            public void setPercentage(Double percentage) { this.percentage = percentage; }
        }
    }
}