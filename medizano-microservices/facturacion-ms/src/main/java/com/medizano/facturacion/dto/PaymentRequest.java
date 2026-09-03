package com.medizano.facturacion.dto;

import com.medizano.facturacion.entity.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotNull(message = "Selecciona un medio de pago")
    private Payment.PaymentMode mode;
    
    @NotNull(message = "Ingresa el monto del pago")
    @DecimalMin(value = "0.0", message = "El monto debe ser positivo")
    private BigDecimal amount;
    
    private String paymentReference;
}

