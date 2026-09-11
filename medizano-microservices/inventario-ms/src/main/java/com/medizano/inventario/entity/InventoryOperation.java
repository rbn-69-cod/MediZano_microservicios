package com.medizano.inventario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_operations", uniqueConstraints =
        @UniqueConstraint(name = "uk_inventory_operation_key", columnNames = "operation_key"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_key", nullable = false, length = 120)
    private String operationKey;

    @Column(nullable = false, length = 30)
    private String operationType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
