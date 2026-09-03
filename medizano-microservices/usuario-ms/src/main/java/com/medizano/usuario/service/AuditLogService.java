package com.medizano.usuario.service;

import com.medizano.usuario.dto.AuditLogResponse;
import com.medizano.usuario.entity.AuditLog;
import com.medizano.usuario.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditLogService {
    
    private final AuditLogRepository auditLogRepository;
    
    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAllAuditLogs() {
        List<AuditLog> logs = auditLogRepository.findTop50ByOrderByTimestampDesc();
        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getLoginLogoutLogs() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<AuditLog> loginLogs = auditLogRepository.findTop50ByActionAndDateRange(
                AuditLog.ActionType.USER_LOGIN.name(), startDate, endDate);
        List<AuditLog> logoutLogs = auditLogRepository.findTop50ByActionAndDateRange(
                AuditLog.ActionType.USER_LOGOUT.name(), startDate, endDate);
        
        List<AuditLog> allLogs = new ArrayList<>(loginLogs);
        allLogs.addAll(logoutLogs);
        
        return allLogs.stream()
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .limit(50)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        List<AuditLog> logs = auditLogRepository.findByDateRange(startDate, endDate);
        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getAuditLogsByUser(Long userId) {
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        List<AuditLog> logs = auditLogRepository.findByUserAndDateRange(userId, startDate, endDate);
        return logs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteAllAuditLogs() {
        auditLogRepository.deleteAll();
    }
    
    @Transactional
    public void deleteLoginLogoutLogs() {
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        
        List<AuditLog> loginLogs = auditLogRepository.findByActionAndDateRange(
                AuditLog.ActionType.USER_LOGIN, startDate, endDate);
        List<AuditLog> logoutLogs = auditLogRepository.findByActionAndDateRange(
                AuditLog.ActionType.USER_LOGOUT, startDate, endDate);
        
        List<AuditLog> allLogs = new ArrayList<>(loginLogs);
        allLogs.addAll(logoutLogs);
        
        auditLogRepository.deleteAll(allLogs);
    }
    
    private AuditLogResponse mapToResponse(AuditLog item) {
        return new AuditLogResponse(
                item.getId(),
                item.getUser() != null ? item.getUser().getId() : null,
                item.getUser() != null ? item.getUser().getUsername() : null,
                item.getUser() != null ? item.getUser().getFullName() : null,
                item.getUser() != null && item.getUser().getRole() != null ? item.getUser().getRole().name() : null,
                item.getAction(),
                item.getEntityType(),
                item.getEntityId(),
                item.getDescription(),
                item.getOldValue(),
                item.getNewValue(),
                item.getTimestamp(),
                item.getIpAddress()
        );
    }
}

