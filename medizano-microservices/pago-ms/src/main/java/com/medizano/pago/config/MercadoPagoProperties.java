package com.medizano.pago.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
@ConfigurationProperties(prefix = "mercadopago")
@Data
public class MercadoPagoProperties {
    private String accessToken = "";
    private String publicKey = "";
    private String baseUrl = "https://api.mercadopago.com";
    private String currency = "PEN";
    private String webhookSecret = "";
    private String checkoutBaseUrl = "http://localhost:4200";
}
