package com.medizano.usuario.service;

import com.medizano.usuario.dto.ChangePasswordRequest;
import com.medizano.usuario.dto.UserResponse;
import com.medizano.usuario.entity.AuditLog;
import com.medizano.usuario.entity.User;
import com.medizano.usuario.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));
        return mapToResponse(user);
    }
    
    @Transactional
    public UserResponse changeUserPassword(Long userId, ChangePasswordRequest request, User adminUser, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));
        
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        
        auditService.log(
            AuditLog.ActionType.USER_PASSWORD_CHANGED,
            adminUser,
            "User",
            user.getId().toString(),
            "Administrador cambió contraseña del usuario: " + user.getUsername(),
            null,
            "Password changed",
            httpRequest
        );
        
        return mapToResponse(user);
    }
    
    @Transactional
    public UserResponse updateUserStatus(Long userId, Boolean active, User adminUser, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));
        
        Boolean oldStatus = user.getActive();
        user.setActive(active);
        userRepository.save(user);
        
        auditService.log(
            AuditLog.ActionType.USER_STATUS_CHANGED,
            adminUser,
            "User",
            user.getId().toString(),
            "Administrador " + (active ? "activó" : "desactivó") + " al usuario: " + user.getUsername(),
            oldStatus.toString(),
            active.toString(),
            httpRequest
        );
        
        return mapToResponse(user);
    }
    
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .active(user.getActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

