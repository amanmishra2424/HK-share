package com.pdfprinting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "paytm")
public class PaytmConfig {
    
    private Merchant merchant = new Merchant();
    private String website;
    private Industry industry = new Industry();
    private Channel channel = new Channel();
    private String paymentUrl;
    private String statusQueryUrl;
    private String callbackUrl;
    
    // Getters and Setters
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    
    public Industry getIndustry() { return industry; }
    public void setIndustry(Industry industry) { this.industry = industry; }
    
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    
    public String getStatusQueryUrl() { return statusQueryUrl; }
    public void setStatusQueryUrl(String statusQueryUrl) { this.statusQueryUrl = statusQueryUrl; }
    
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    
    // Inner classes
    public static class Merchant {
        private String id;
        private String key;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
    }
    
    public static class Industry {
        private String type;
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
    
    public static class Channel {
        private String id;
        
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }
}