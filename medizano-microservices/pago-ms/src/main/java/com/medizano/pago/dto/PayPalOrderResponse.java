package com.medizano.pago.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayPalOrderResponse {
    private Long ordenId;
    private String numeroOrden;
    private String paypalOrderId;
    private String status;
    private String approveUrl;
    private BigDecimal amountPen;
    private BigDecimal amountUsd;
    private String currency;
    private String clientId;
}

