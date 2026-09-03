package com.medizano.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "bill_items", indexes = {
    @Index(name = "idx_fbill_item_bill", columnList = "bill_id"),
    @Index(name = "idx_fbill_item_med", columnList = "medicineId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"bill"})
@EqualsAndHashCode(exclude = {"bill"})
public class BillItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;
    
    @Column(nullable = false)
    private Long medicineId;
    
    @Column(length = 200)
    private String medicineName;
    
    private Long batchId;
    
    @Column(nullable = false, length = 50)
    private String batchNumber;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal gstPercentage;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal gstAmount;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
}

