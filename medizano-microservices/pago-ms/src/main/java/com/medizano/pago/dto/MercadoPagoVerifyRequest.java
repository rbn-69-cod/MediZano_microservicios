package com.medizano.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MercadoPagoVerifyRequest {
    @NotNull(message = "El ID de la orden es obligatorio")
    private Long ordenId;

    @NotBlank(message = "El Payment ID de Mercado Pago es obligatorio")
    private String paymentId;

    private String preferenceId;
}

