package com.medizano.cliente.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteDTO {
    private Long id;
    private String nombre;
    private String documento;
    private String telefono;
    private String email;
    private String direccion;
    private Boolean estado;
    private LocalDateTime createdAt;
}

