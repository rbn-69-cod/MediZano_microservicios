package com.medizano.facturacion.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalFeignConfig {
    @Bean
    RequestInterceptor internalServiceTokenInterceptor(
            @Value("${security.internal-service-token}") String token) {
        return template -> template.header("X-Internal-Service-Token", token);
    }
}
