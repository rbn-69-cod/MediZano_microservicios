package com.medizano.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Respuesta detallada de un lote farmacéutico y su disponibilidad de stock")
public class BatchResponse {

    @Schema(description = "Identificador único del lote", example = "1")
    private Long id;

    @Schema(description = "ID del medicamento asociado", example = "1")
    private Long medicineId;

    @Schema(description = "Nombre del medicamento asociado", example = "Paracetamol 500mg")
    private String medicineName;

    @Schema(description = "Código o número de lote del fabricante", example = "LOT-2026-X9")
    private String batchNumber;

    @Schema(description = "Fecha de caducidad del lote (YYYY-MM-DD)", example = "2027-06-30")
    private LocalDate expiryDate;

    @Schema(description = "Precio de costo unitario", example = "3.20")
    private BigDecimal purchasePrice;

    @Schema(description = "Precio de venta unitario", example = "6.00")
    private BigDecimal sellingPrice;

    @Schema(description = "Unidades actualmente disponibles en este lote", example = "50")
    private Integer quantityAvailable;

    @Schema(description = "Indica si el lote ya ha expirado a la fecha actual", example = "false")
    private Boolean expired;

    @Schema(description = "Fecha de registro del lote", example = "2026-09-02T11:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de última modificación", example = "2026-09-03T15:20:00")
    private LocalDateTime updatedAt;
}

