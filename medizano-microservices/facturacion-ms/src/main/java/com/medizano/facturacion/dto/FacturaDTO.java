package com.medizano.facturacion.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaDTO {
    private Long id;
    private String numeroFactura;
    private Long ordenId;
    private String numeroOrden;
    private Long clienteId;
    private String clienteNombre;
    private String metodoPago;
    private String referenciaPago;
    private BigDecimal subtotal;
    private BigDecimal impuesto;
    private BigDecimal total;
    private String estado;
    private List<ItemFacturaDTO> items;
    private LocalDateTime createdAt;
}

