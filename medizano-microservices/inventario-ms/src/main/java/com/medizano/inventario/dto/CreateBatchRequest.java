package com.medizano.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "Solicitud para registrar un nuevo lote farmacéutico")
public class CreateBatchRequest {

    @NotNull(message = "El ID de medicamento es obligatorio")
    @Schema(description = "ID del medicamento asociado", example = "1")
    private Long medicineId;
    
    @NotBlank(message = "El número de lote es obligatorio")
    @Schema(description = "Código o número de lote del fabricante", example = "LOT-2026-X9")
    private String batchNumber;
    
    @NotNull(message = "La fecha de vencimiento es obligatoria")
    @Schema(description = "Fecha de caducidad del lote (YYYY-MM-DD)", example = "2027-06-30")
    private LocalDate expiryDate;
    
    @NotNull(message = "El precio de compra es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de compra debe ser mayor a 0")
    @Schema(description = "Precio de compra unitario al proveedor", example = "3.20")
    private BigDecimal purchasePrice;
    
    @NotNull(message = "El precio de venta es obligatorio")
    @DecimalMin(value = "0.0", inclusive = false, message = "El precio de venta debe ser mayor a 0")
    @Schema(description = "Precio de venta al público por unidad", example = "6.00")
    private BigDecimal sellingPrice;
    
    @NotNull(message = "La cantidad disponible es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser al menos 1")
    @Schema(description = "Cantidad de unidades físicas ingresadas", example = "50")
    private Integer quantityAvailable;
    
    @Schema(description = "Lista opcional de códigos de barra serializados individuales", example = "[\"7751234567890-001\", \"7751234567890-002\"]")
    private List<String> barcodes;
}

