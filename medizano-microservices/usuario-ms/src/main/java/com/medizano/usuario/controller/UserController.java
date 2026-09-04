package com.medizano.usuario.controller;

import com.medizano.usuario.dto.ChangePasswordRequest;
import com.medizano.usuario.dto.UserResponse;
import com.medizano.usuario.entity.User;
import com.medizano.usuario.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Gestión de Usuarios", description = "Administración de usuarios, roles de seguridad, cambio de contraseña y estados")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    @Operation(summary = "Listar todos los usuarios", description = "Obtiene la lista de usuarios registrados con sus roles asignados (ADMIN, PHARMACIST, CASHIER). Requiere rol ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de usuarios recuperada exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado - Token JWT ausente o inválido"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
    
    @GetMapping("/{userId}")
    @Operation(summary = "Obtener usuario por ID", description = "Consulta el detalle de un usuario en el sistema por su identificador primario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UserResponse> getUserById(
            @Parameter(description = "ID del usuario", example = "1", required = true)
            @PathVariable Long userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }
    
    @PutMapping("/{userId}/password")
    @Operation(summary = "Cambiar contraseña de usuario", description = "Actualiza de manera segura la contraseña de un usuario encriptándola con algoritmo BCrypt.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contraseña actualizada exitosamente", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "La nueva contraseña no cumple los requisitos de longitud o complejidad"),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UserResponse> changeUserPassword(
            @Parameter(description = "ID del usuario a modificar", example = "1", required = true)
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User adminUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.changeUserPassword(userId, request, adminUser, httpRequest));
    }
    
    @PutMapping("/{userId}/status")
    @Operation(summary = "Activar o desactivar usuario", description = "Modifica el estado de activación de un usuario permitiendo o bloqueando su acceso al sistema.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado de usuario actualizado", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN"),
            @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<UserResponse> updateUserStatus(
            @Parameter(description = "ID del usuario a modificar", example = "1", required = true)
            @PathVariable Long userId,
            @Parameter(description = "true para activar, false para desactivar", example = "true", required = true)
            @RequestParam Boolean active,
            @AuthenticationPrincipal User adminUser,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUserStatus(userId, active, adminUser, httpRequest));
    }
}

