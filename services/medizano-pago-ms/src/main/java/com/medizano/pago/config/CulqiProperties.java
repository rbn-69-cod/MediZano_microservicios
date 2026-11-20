package com.medizano.pago.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "culqi")
public class CulqiProperties {
    private String apiBaseUrl = "https://api.culqi.com/v2";
    private String publicKey;
    private String secretKey;
    private String webhookSecret;
    private int qrExpirationMinutes = 15;
}
