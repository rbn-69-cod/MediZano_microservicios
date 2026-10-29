package com.medizano.proveedor.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "compras", indexes = {
        @Index(name = "idx_compras_numero", columnList = "numeroDocumento"),
        @Index(name = "idx_compras_proveedor", columnList = "proveedor_id"),
        @Index(name = "idx_compras_estado", columnList = "estado")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El número de documento o compra es obligatorio")
    @Column(nullable = false, length = 50)
    private String numeroDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TipoDocumento tipoDocumento = TipoDocumento.FACTURA;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false)
    private LocalDate fechaEmision;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @Column
    private LocalDateTime fechaRecepcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoCompra estado = EstadoCompra.PENDIENTE;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal igv = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String observaciones;

    @Column(length = 100)
    private String usuarioRegistro;

    @Column(length = 100)
    private String usuarioRecepcion;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DetalleCompra> detalles = new ArrayList<>();

    public enum EstadoCompra {
        BORRADOR,
        PENDIENTE,
        ORDEN_ACEPTADA,
        EMPAQUETANDO,
        EN_CAMINO,
        EN_ENTREGA,
        POR_VERIFICAR,
        VERIFICANDO,
        RECIBIDA,
        CANCELADA
    }

    public enum TipoDocumento {
        FACTURA,
        BOLETA,
        GUIA_REMISION,
        ORDEN_COMPRA
    }

    @PrePersist
    public void prePersist() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now();
        }
        if (fechaEmision == null) {
            fechaEmision = LocalDate.now();
        }
        if (estado == null) {
            estado = EstadoCompra.PENDIENTE;
        }
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (igv == null) igv = BigDecimal.ZERO;
        if (descuento == null) descuento = BigDecimal.ZERO;
        if (total == null) total = BigDecimal.ZERO;
    }
}

