package com.medizano.inventario.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Datos del proveedor obtenidos vía OpenFeign")
public class ProveedorDTO {
    private Long id;
    private String razonSocial;
    private String emailContacto;
    private String ruc;
}

