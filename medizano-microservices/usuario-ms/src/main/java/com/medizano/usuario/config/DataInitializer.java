package com.medizano.usuario.config;

import com.medizano.usuario.entity.User;
import com.medizano.usuario.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Value("${medizano.default-password:admin123}")
    private String configuredDefaultPassword;

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String defaultPassword = (configuredDefaultPassword == null || configuredDefaultPassword.trim().isEmpty())
                    ? "admin123" : configuredDefaultPassword.trim();
            String encodedDefaultPassword = passwordEncoder.encode(defaultPassword);

            createIfNotFound(userRepository, "admin", encodedDefaultPassword, "admin@medizano.pe", "Administrador del Sistema", User.Role.ADMIN);
            createIfNotFound(userRepository, "cajero", encodedDefaultPassword, "cajero@medizano.pe", "Cajero Principal", User.Role.CASHIER);
            createIfNotFound(userRepository, "inventario", encodedDefaultPassword, "inventario@medizano.pe", "Monitor de Inventario", User.Role.STOCK_MONITOR);
            createIfNotFound(userRepository, "almacen", encodedDefaultPassword, "almacen@medizano.pe", "Encargado de Almacén", User.Role.STOCK_KEEPER);
            createIfNotFound(userRepository, "soporte", encodedDefaultPassword, "soporte@medizano.pe", "Atención al Cliente", User.Role.CUSTOMER_SUPPORT);
            createIfNotFound(userRepository, "analista", encodedDefaultPassword, "analista@medizano.pe", "Analista de Datos", User.Role.ANALYST);
            createIfNotFound(userRepository, "gerente", encodedDefaultPassword, "gerente@medizano.pe", "Gerente de Tienda", User.Role.MANAGER);

            System.out.println(">>> [usuario-ms] Usuarios del sistema inicializados con éxito.");
        };
    }

    private void createIfNotFound(UserRepository userRepository, String username, String password,
                                  String email, String fullName, User.Role role) {
        if (!userRepository.existsByUsername(username)) {
            User newUser = User.builder()
                    .username(username)
                    .password(password)
                    .email(email)
                    .fullName(fullName)
                    .role(role)
                    .active(true)
                    .build();
            userRepository.save(newUser);
            System.out.println(">>> [usuario-ms] Usuario creado: " + username + " (" + role + ")");
        }
    }
}

