package com.medizano.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "batches", indexes = {
    @Index(name = "idx_batch_med", columnList = "medicineId"),
    @Index(name = "idx_batch_exp", columnList = "expiryDate"),
    @Index(name = "idx_batch_num", columnList = "batchNumber"),
    @Index(name = "idx_batches_fefo_pos", columnList = "medicineId, estado, expiryDate, quantityAvailable"),
    @Index(name = "idx_batches_cron_eval", columnList = "expiryDate, estado, quantityAvailable")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long medicineId;
    
    @Column(length = 200)
    private String medicineName;
    
    @Column(nullable = false, length = 50)
    private String batchNumber;
    
    @Column(nullable = false)
    private LocalDate expiryDate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal purchasePrice;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPrice;
    
    @Column(nullable = false)
    private Integer quantityAvailable;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EstadoLote estado = EstadoLote.DISPONIBLE;
    
    @Column(name = "proveedor_id")
    private Long proveedorId;
    
    @Column(name = "numero_factura_compra", length = 50)
    private String numeroFacturaCompra;
    
    @Column(name = "fecha_ultima_evaluacion")
    private LocalDateTime fechaUltimaEvaluacion;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (estado == null) {
            estado = EstadoLote.DISPONIBLE;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDate.now());
    }

    public long getDiasParaVencer(LocalDate hoy) {
        if (expiryDate == null) return 0;
        return ChronoUnit.DAYS.between(hoy, expiryDate);
    }

    public boolean isAptoVentaFefo(LocalDate minSafeDate) {
        return (estado == EstadoLote.DISPONIBLE || estado == EstadoLote.ALERTA_CANJE)
                && expiryDate != null 
                && expiryDate.isAfter(minSafeDate)
                && quantityAvailable != null 
                && quantityAvailable > 0;
    }
}
