package com.medizano.proveedor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Datos esenciales de proveedor para consultas batch y notificaciones")
public class ProveedorDTO {
    private Long id;
    private String razonSocial;
    private String emailContacto;
    private String ruc;
}

