package com.medizano.inventario.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioDTO {
    private Long id;
    private Long productoId;
    private Integer stockActual;
    private Integer stockMinimo;
    private Boolean stockBajo;
    private LocalDateTime updatedAt;
}

