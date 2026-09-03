package com.medizano.usuario.controller;

import com.medizano.usuario.dto.ChangePasswordRequest;
import com.medizano.usuario.dto.UserResponse;
import com.medizano.usuario.entity.User;
import com.medizano.usuario.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "User Management", description = "Gestión de Usuarios (Admin)")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    @Operation(summary = "Listar todos los usuarios")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Obtener usuario por ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }
    
    @PutMapping("/{userId}/password")
    @Operation(summary = "Cambiar contraseña de usuario")
    public ResponseEntity<UserResponse> changeUserPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User adminUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.changeUserPassword(userId, request, adminUser, httpRequest));
    }
    
    @PutMapping("/{userId}/status")
    @Operation(summary = "Activar o desactivar usuario")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Boolean active,
            @AuthenticationPrincipal User adminUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUserStatus(userId, active, adminUser, httpRequest));
    }
}

