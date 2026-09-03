package com.medizano.usuario.controller;

import com.medizano.usuario.dto.AuditLogResponse;
import com.medizano.usuario.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Audit Logs", description = "Auditoría de Actividades (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    @GetMapping("/all")
    @Operation(summary = "Listar todos los registros de auditoría")
    public ResponseEntity<List<AuditLogResponse>> getAllAuditLogs() {
        return ResponseEntity.ok(auditLogService.getAllAuditLogs());
    }
    
    @GetMapping("/login-logout")
    @Operation(summary = "Listar historial de inicio y cierre de sesión")
    public ResponseEntity<List<AuditLogResponse>> getLoginLogoutLogs() {
        return ResponseEntity.ok(auditLogService.getLoginLogoutLogs());
    }
    
    @GetMapping("/date-range")
    @Operation(summary = "Listar auditoría por rango de fechas")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByDateRange(startDate, endDate));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Listar auditoría por usuario")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByUser(userId));
    }
    
    @DeleteMapping("/all")
    @Operation(summary = "Eliminar todos los registros de auditoría")
    public ResponseEntity<Void> deleteAllAuditLogs() {
        auditLogService.deleteAllAuditLogs();
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/login-logout")
    @Operation(summary = "Eliminar registros de login/logout")
    public ResponseEntity<Void> deleteLoginLogoutLogs() {
        auditLogService.deleteLoginLogoutLogs();
        return ResponseEntity.noContent().build();
    }
}

