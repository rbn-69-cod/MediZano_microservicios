package com.medizano.pago.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfigResponse {
    // PayPal
    private String payPalClientId;
    private String payPalCurrency;
    private String payPalBaseUrl;

    // Mercado Pago
    private String mpPublicKey;
    private String mpCurrency;
}

