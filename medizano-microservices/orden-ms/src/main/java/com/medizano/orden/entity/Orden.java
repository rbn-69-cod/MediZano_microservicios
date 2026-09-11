package com.medizano.orden.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ordenes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Orden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 50)
    private String numeroOrden;

    @Column(nullable = false)
    private LocalDateTime fecha;

    private Long clienteId;
    private String clienteNombre;
    private String clienteEmail;

    private Long usuarioId;
    private String usuarioNombre;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @NotNull
    @DecimalMin(value = "0.0")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal impuesto; // IGV 18%

    @NotNull
    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOrden estado;

    @Column(length = 30)
    private String metodoPago; // EFECTIVO, PAYPAL

    @Column(length = 100)
    private String referenciaPago;

    @Builder.Default
    private Boolean inventarioProcesado = false;

    @Builder.Default
    private Boolean facturaGenerada = false;

    @Column(length = 500)
    private String errorProcesamiento;

    @Builder.Default
    @OneToMany(mappedBy = "orden", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleOrden> detalles = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
        if (estado == null) {
            estado = EstadoOrden.PENDING;
        }
        if (inventarioProcesado == null) {
            inventarioProcesado = false;
        }
        if (facturaGenerada == null) {
            facturaGenerada = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum EstadoOrden {
        PENDING, PAGADA, CANCELADA, REEMBOLSADA
    }
}
