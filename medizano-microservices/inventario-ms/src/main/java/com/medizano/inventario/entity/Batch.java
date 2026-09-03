package com.medizano.inventario.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "batches", indexes = {
    @Index(name = "idx_batch_med", columnList = "medicineId"),
    @Index(name = "idx_batch_exp", columnList = "expiryDate"),
    @Index(name = "idx_batch_num", columnList = "batchNumber")
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
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }
}

