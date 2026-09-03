package com.medizano.pago.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoPreferenceResponse {
    private Long ordenId;
    private String numeroOrden;
    private String preferenceId;
    private String initPoint;
    private String sandboxInitPoint;
    private BigDecimal amountPen;
    private String currency;
    private String publicKey;
}

