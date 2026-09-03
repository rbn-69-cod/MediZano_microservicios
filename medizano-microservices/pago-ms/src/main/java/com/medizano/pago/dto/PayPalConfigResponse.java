package com.medizano.pago.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayPalConfigResponse {
    private String clientId;
    private String currency;
    private String baseUrl;
}

