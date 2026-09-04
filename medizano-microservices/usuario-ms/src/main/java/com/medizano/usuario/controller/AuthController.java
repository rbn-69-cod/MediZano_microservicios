package com.medizano.usuario.controller;

import com.medizano.usuario.dto.AuthResponse;
import com.medizano.usuario.dto.LoginRequest;
import com.medizano.usuario.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Autenticación de usuarios, emisión de tokens JWT Bearer y control de sesiones")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/login")
    @Operation(summary = "Inicio de sesión y emisión de JWT", description = "Valida las credenciales del usuario (con BCrypt), registra el acceso en auditoría y retorna un token JWT Bearer.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticación exitosa", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Solicitud inválida o campos requeridos vacíos"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o usuario desactivado")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }
    
    @PostMapping("/logout")
    @Operation(summary = "Cierre de sesión", description = "Registra la desconexión del usuario en los registros de auditoría.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión cerrada y registrada en auditoría exitosamente"),
            @ApiResponse(responseCode = "401", description = "No autorizado")
    })
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(httpRequest);
        return ResponseEntity.ok().build();
    }
}

