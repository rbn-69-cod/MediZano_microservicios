package com.medizano.usuario.dto;

import com.medizano.usuario.entity.AuditLog;
import lombok.Data;

@Data
public class AuditEventRequest {
    private AuditLog.ActionType action;
    private String entityType;
    private String entityId;
    private String description;
    private String oldValue;
    private String newValue;
}
