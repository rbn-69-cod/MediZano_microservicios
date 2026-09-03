package com.medizano.pago.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "paypal")
@Data
public class PayPalProperties {
    private String baseUrl = "https://api-m.sandbox.paypal.com";
    private String clientId = "";
    private String clientSecret = "";
    private String currency = "USD";
    private BigDecimal exchangeRate = new BigDecimal("3.75");
    private String webhookUrl = "";
}

