package com.medizano.facturacion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "return_items", indexes = {
    @Index(name = "idx_fret_item_return", columnList = "return_id"),
    @Index(name = "idx_fret_item_med", columnList = "medicineId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_id", nullable = false)
    private Return returnEntity;
    
    @Column(nullable = false)
    private Long medicineId;
    
    @Column(length = 200)
    private String medicineName;
    
    private Long batchId;
    
    @Column(name = "batch_number", nullable = false, length = 50)
    private String batchNumber;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;
}

