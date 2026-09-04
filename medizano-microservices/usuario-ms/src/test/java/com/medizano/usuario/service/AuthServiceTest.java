package com.medizano.usuario.service;

import com.medizano.usuario.dto.AuthResponse;
import com.medizano.usuario.dto.LoginRequest;
import com.medizano.usuario.entity.User;
import com.medizano.usuario.repository.UserRepository;
import com.medizano.usuario.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("admin")
                .password("encoded_pass")
                .fullName("Administrador Principal")
                .email("admin@medizano.pe")
                .role(User.Role.ADMIN)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("1. Login exitoso retorna JWT y datos del usuario")
    void testLoginExitoso() {
        LoginRequest request = new LoginRequest("admin", "admin123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("sample.jwt.token");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(sampleUser));

        AuthResponse response = authService.login(request, httpRequest);

        assertNotNull(response);
        assertEquals("sample.jwt.token", response.getToken());
        assertEquals("admin", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertEquals("Administrador Principal", response.getFullName());
        assertEquals(1L, response.getUserId());

        verify(auditService, times(1)).log(any(), eq(sampleUser), eq("User"), eq("1"), anyString(), isNull(), isNull(), eq(httpRequest));
    }

    @Test
    @DisplayName("2. Login con credenciales inválidas lanza BadCredentialsException")
    void testLoginBadCredentials() {
        LoginRequest request = new LoginRequest("admin", "wrong_pass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request, httpRequest));
        verify(tokenProvider, never()).generateToken(any());
        verify(userRepository, never()).findByUsername(any());
    }

    @Test
    @DisplayName("3. Login falla si el usuario autenticado no existe en el repositorio")
    void testLoginUsuarioNoEncontradoEnBd() {
        LoginRequest request = new LoginRequest("desconocido", "123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(tokenProvider.generateToken(authentication)).thenReturn("token");
        when(userRepository.findByUsername("desconocido")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.login(request, httpRequest));
        assertTrue(ex.getMessage().contains("Usuario no encontrado"));
    }
}
