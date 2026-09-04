package com.medizano.usuario.controller;

import com.medizano.usuario.dto.AuditLogResponse;
import com.medizano.usuario.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Auditoría de Seguridad", description = "Bitácora inmutable de eventos de seguridad, trazabilidad de inicios de sesión y operaciones administrativas")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {
    
    private final AuditLogService auditLogService;
    
    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }
    
    @GetMapping("/all")
    @Operation(summary = "Listar todos los registros de auditoría", description = "Obtiene la bitácora completa de eventos registrados en el sistema. Requiere rol ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registros de auditoría recuperados exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<List<AuditLogResponse>> getAllAuditLogs() {
        return ResponseEntity.ok(auditLogService.getAllAuditLogs());
    }
    
    @GetMapping("/login-logout")
    @Operation(summary = "Listar historial de inicio y cierre de sesión", description = "Filtra la bitácora para mostrar únicamente los eventos de autenticación y desconexión de usuarios.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial de accesos recuperado exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<List<AuditLogResponse>> getLoginLogoutLogs() {
        return ResponseEntity.ok(auditLogService.getLoginLogoutLogs());
    }
    
    @GetMapping("/date-range")
    @Operation(summary = "Listar auditoría por rango de fechas", description = "Obtiene las acciones de auditoría ocurridas entre dos marcas temporales ISO-8601.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registros en el rango recuperados"),
            @ApiResponse(responseCode = "400", description = "Formato de fecha inválido"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByDateRange(
            @Parameter(description = "Fecha y hora inicial (ISO-8601)", example = "2026-09-01T00:00:00", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "Fecha y hora final (ISO-8601)", example = "2026-09-03T23:59:59", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByDateRange(startDate, endDate));
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Listar auditoría por usuario", description = "Obtiene la relación cronológica de acciones efectuadas por un usuario específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registros del usuario recuperados"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<List<AuditLogResponse>> getAuditLogsByUser(
            @Parameter(description = "ID del usuario auditado", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(auditLogService.getAuditLogsByUser(userId));
    }
    
    @DeleteMapping("/all")
    @Operation(summary = "Eliminar todos los registros de auditoría", description = "Depura y vacía la tabla completa de registros de auditoría. Requiere rol ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Registros eliminados correctamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<Void> deleteAllAuditLogs() {
        auditLogService.deleteAllAuditLogs();
        return ResponseEntity.noContent().build();
    }
    
    @DeleteMapping("/login-logout")
    @Operation(summary = "Eliminar registros de login/logout", description = "Elimina únicamente los eventos de autenticación y cierre de sesión de la tabla de auditoría.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logs de sesión eliminados"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<Void> deleteLoginLogoutLogs() {
        auditLogService.deleteLoginLogoutLogs();
        return ResponseEntity.noContent().build();
    }
}

