package com.medizano.inventario.dto;

import com.medizano.inventario.entity.MovimientoInventario;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoDTO {
    private Long id;
    private Long productoId;
    private MovimientoInventario.TipoMovimiento tipo;
    private Integer cantidad;
    private String referencia;
    private LocalDateTime fecha;
}

