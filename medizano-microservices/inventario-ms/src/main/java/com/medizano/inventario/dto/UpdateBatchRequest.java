package com.medizano.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateBatchRequest {
    @NotBlank(message = "El número de lote es obligatorio")
    private String batchNumber;
    
    @NotNull(message = "La fecha de vencimiento es obligatoria")
    private LocalDate expiryDate;
    
    @NotNull(message = "El precio de compra es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de compra debe ser mayor a 0")
    private BigDecimal purchasePrice;
    
    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    private BigDecimal sellingPrice;

    @jakarta.validation.constraints.Min(value = 0, message = "La cantidad disponible no puede ser negativa")
    private Integer quantityAvailable;
}

