package com.medizano.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resumen de stock y estado de inventario de un producto")
public class InventarioDTO {

    @Schema(description = "ID del registro de inventario", example = "1")
    private Long id;

    @Schema(description = "ID del producto", example = "1")
    private Long productoId;

    @Schema(description = "Cantidad física disponible en almacén", example = "120")
    private Integer stockActual;

    @Schema(description = "Umbral de stock mínimo para reorden", example = "15")
    private Integer stockMinimo;

    @Schema(description = "Indica si el stock actual está por debajo del mínimo", example = "false")
    private Boolean stockBajo;

    @Schema(description = "Fecha y hora de la última modificación de stock", example = "2026-09-03T18:45:00")
    private LocalDateTime updatedAt;
}

