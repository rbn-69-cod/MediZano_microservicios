package com.medizano.pago.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "culqi")
@Data
public class CulqiProperties {
    /** Llaves inyectadas exclusivamente por variables de entorno. */
    private String publicKey = "";
    private String secretKey = "";
    private String baseUrl = "https://api.culqi.com/v2";
    private String currency = "PEN";
    private Integer orderExpirationMinutes = 30;
}
