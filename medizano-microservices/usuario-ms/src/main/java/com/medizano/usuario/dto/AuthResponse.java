package com.medizano.usuario.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String type;
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String role;

    public static AuthResponse of(String token, Long userId, String username, String fullName, String email, String role) {
        AuthResponse r = new AuthResponse();
        r.setToken(token);
        r.setType("Bearer");
        r.setUserId(userId);
        r.setUsername(username);
        r.setFullName(fullName);
        r.setEmail(email);
        r.setRole(role);
        return r;
    }
}

