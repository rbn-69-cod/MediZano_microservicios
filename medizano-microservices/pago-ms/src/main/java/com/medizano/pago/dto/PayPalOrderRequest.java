package com.medizano.pago.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayPalOrderRequest {
    @NotNull(message = "El ID de la orden es obligatorio")
    private Long ordenId;

    private String returnUrl;
    private String cancelUrl;
}

