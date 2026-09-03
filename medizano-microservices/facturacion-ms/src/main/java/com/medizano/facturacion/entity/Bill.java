package com.medizano.facturacion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bills", indexes = {
    @Index(name = "idx_fbill_number", columnList = "billNumber", unique = true),
    @Index(name = "idx_fbill_date", columnList = "billDate"),
    @Index(name = "idx_fbill_cashier", columnList = "cashierId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"billItems", "payments"})
@EqualsAndHashCode(exclude = {"billItems", "payments"})
public class Bill {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 50)
    private String billNumber;
    
    @Column(nullable = false)
    private LocalDateTime billDate;
    
    private Long cashierId;
    
    @Column(length = 100)
    private String cashierName;
    
    @Column(length = 200)
    private String customerName;
    
    @Column(length = 20)
    private String customerPhone;

    @Column(length = 100)
    private String customerEmail;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalGst;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal cashTendered = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal changeAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;
    
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<BillItem> billItems = new ArrayList<>();
    
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean cancelled = false;
    
    private String cancellationReason;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (billDate == null) {
            billDate = LocalDateTime.now();
        }
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (totalGst == null) totalGst = BigDecimal.ZERO;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PaymentStatus {
        PENDING, PAID, PARTIALLY_PAID, REFUNDED
    }
}

