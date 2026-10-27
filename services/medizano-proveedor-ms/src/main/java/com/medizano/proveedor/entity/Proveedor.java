package com.medizano.proveedor.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "proveedores", indexes = {
        @Index(name = "idx_proveedores_ruc", columnList = "ruc"),
        @Index(name = "idx_proveedores_razon", columnList = "razonSocial")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El RUC es obligatorio")
    @Size(min = 11, max = 11, message = "El RUC debe tener exactamente 11 dígitos")
    @Pattern(regexp = "^(10|15|17|20)\\d{9}$", message = "El RUC peruano debe ser de 11 dígitos numéricos y comenzar con 10, 15, 17 o 20")
    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @NotBlank(message = "La razón social es obligatoria")
    @Column(nullable = false, length = 150)
    private String razonSocial;

    @Column(length = 150)
    private String nombreComercial;

    @Column(length = 150)
    private String contactoResponsable;

    @JsonAlias({"direccion", "direccionFiscal"})
    @Column(length = 255)
    private String direccionFiscal;

    @JsonAlias({"telefono", "telefonoContacto"})
    @Column(length = 20)
    private String telefonoContacto;

    @JsonAlias({"email", "emailContacto"})
    @Column(length = 150)
    private String emailContacto;

    @Column(length = 50)
    private String registroDigemid;

    @Column(length = 500)
    private String observaciones;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Column
    private LocalDateTime fechaActualizacion;

    @JsonProperty("direccion")
    public String getDireccion() {
        return direccionFiscal;
    }

    @JsonProperty("telefono")
    public String getTelefono() {
        return telefonoContacto;
    }

    @JsonProperty("email")
    public String getEmail() {
        return emailContacto;
    }

    @PrePersist
    public void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (activo == null) {
            activo = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }
}
