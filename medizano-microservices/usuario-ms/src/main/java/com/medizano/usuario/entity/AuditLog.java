package com.medizano.usuario.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_usuario_audit_user", columnList = "user_id"),
    @Index(name = "idx_usuario_audit_action", columnList = "action"),
    @Index(name = "idx_usuario_audit_date", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ActionType action;
    
    @Column(nullable = false, length = 100)
    private String entityType;
    
    @Column(length = 100)
    private String entityId;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(columnDefinition = "TEXT")
    private String oldValue;
    
    @Column(columnDefinition = "TEXT")
    private String newValue;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(length = 50)
    private String ipAddress;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
    
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    
    public ActionType getAction() { return action; }
    public void setAction(ActionType action) { this.action = action; }
    
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public enum ActionType {
        BILL_CREATED,
        BILL_CANCELLED,
        PAYMENT_RECEIVED,
        REFUND_PROCESSED,
        STOCK_ADJUSTED,
        STOCK_UPDATED,
        PRICE_OVERRIDE,
        MEDICINE_ADDED,
        MEDICINE_UPDATED,
        MEDICINE_DELETED,
        BATCH_ADDED,
        BATCH_UPDATED,
        BATCH_DELETED,
        USER_LOGIN,
        USER_LOGOUT,
        USER_PASSWORD_CHANGED,
        USER_STATUS_CHANGED
    }
}
