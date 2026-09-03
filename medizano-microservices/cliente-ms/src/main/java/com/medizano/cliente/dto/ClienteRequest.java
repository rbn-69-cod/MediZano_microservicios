package com.medizano.cliente.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteRequest {
    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String nombre;

    private String documento;
    private String telefono;
    private String email;
    private String direccion;
    private Boolean estado;
}

