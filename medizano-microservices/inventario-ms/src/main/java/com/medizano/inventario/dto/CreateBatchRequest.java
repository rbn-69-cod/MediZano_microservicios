package com.medizano.inventario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CreateBatchRequest {
    @NotNull(message = "El ID de medicamento es obligatorio")
    private Long medicineId;
    
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
    
    @NotNull(message = "La cantidad disponible es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    private Integer quantityAvailable;
    
    private List<String> barcodes;
}

