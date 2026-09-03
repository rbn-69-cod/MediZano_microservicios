package com.medizano.orden.dto;

import com.medizano.orden.entity.Orden;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrdenDTO {
    private Long id;
    private String numeroOrden;
    private LocalDateTime fecha;
    private Long clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private Long usuarioId;
    private String usuarioNombre;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private Orden.EstadoOrden estado;
    private String metodoPago;
    private String referenciaPago;
    private List<DetalleOrdenDTO> detalles;
    private LocalDateTime createdAt;
}

