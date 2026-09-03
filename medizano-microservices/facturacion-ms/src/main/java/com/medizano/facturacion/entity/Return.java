package com.medizano.facturacion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "returns", indexes = {
    @Index(name = "idx_fret_bill", columnList = "original_bill_id"),
    @Index(name = "idx_fret_date", columnList = "returnDate"),
    @Index(name = "idx_fret_num", columnList = "returnNumber", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Return {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String returnNumber;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_bill_id", nullable = false)
    private Bill originalBill;
    
    private Long processedById;
    
    @Column(length = 100)
    private String processedByName;
    
    @Column(nullable = false)
    private LocalDateTime returnDate;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal refundAmount;
    
    @Column(nullable = false, length = 500)
    private String reason;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType returnType;
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (returnDate == null) {
            returnDate = LocalDateTime.now();
        }
    }
    
    public enum ReturnType {
        FULL, PARTIAL
    }
}

