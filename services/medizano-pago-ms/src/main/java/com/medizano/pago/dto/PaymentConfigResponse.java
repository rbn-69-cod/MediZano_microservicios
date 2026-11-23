package com.medizano.pago.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentConfigResponse {
    // Culqi
    private String culqiPublicKey;
    private String culqiCurrency;

    // PayPal
    private String payPalClientId;
    private String payPalCurrency;
    private String payPalBaseUrl;

    // Mercado Pago
    private String mpPublicKey;
    private String mpCurrency;
}

