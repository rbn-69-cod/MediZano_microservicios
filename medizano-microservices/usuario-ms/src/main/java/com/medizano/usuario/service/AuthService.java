package com.medizano.usuario.service;

import com.medizano.usuario.dto.LoginRequest;
import com.medizano.usuario.dto.AuthResponse;
import com.medizano.usuario.entity.AuditLog;
import com.medizano.usuario.entity.User;
import com.medizano.usuario.repository.UserRepository;
import com.medizano.usuario.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final AuditService auditService;
    
    public AuthService(AuthenticationManager authenticationManager, JwtTokenProvider tokenProvider,
                      UserRepository userRepository, AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }
    
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.generateToken(authentication);
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        auditService.log(AuditLog.ActionType.USER_LOGIN, user, "User", 
                        user.getId().toString(), "Inicio de sesión",
                        null, null, httpRequest);
        
        return AuthResponse.of(
                token,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name()
        );
    }
    
    public void logout(HttpServletRequest httpRequest) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByUsername(username).orElse(null);
            
            if (user != null) {
                auditService.log(AuditLog.ActionType.USER_LOGOUT, user, "User", 
                        user.getId().toString(), "Cierre de sesión",
                        null, null, httpRequest);
            }
        } catch (Exception ignored) {
        }
    }
}

