package com.medizano.cliente.config;

import com.medizano.cliente.entity.Cliente;
import com.medizano.cliente.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final ClienteRepository clienteRepository;

    @Bean
    public CommandLineRunner initClienteData() {
        return args -> {
            seedCliente("Juan Pérez Rodríguez", "12345678", "987654321",
                    "juan.perez@gmail.com", "Av. Arequipa 1234, Lima");

            seedCliente("María García López", "87654321", "912345678",
                    "maria.garcia@hotmail.com", "Jr. Huancavelica 456, Trujillo");

            seedCliente("Clínica San Borja S.A.C.", "20123456789", "014567890",
                    "compras@clinicasanborja.pe", "Av. Guardia Civil 385, San Borja, Lima");

            log.info(">>> [cliente-ms] Clientes iniciales inicializados e idempotentes.");
        };
    }

    private void seedCliente(String nombre, String documento, String telefono, String email, String direccion) {
        Cliente cliente = clienteRepository.findByDocumento(documento).orElse(null);
        if (cliente == null) {
            cliente = Cliente.builder()
                    .nombre(nombre)
                    .documento(documento)
                    .telefono(telefono)
                    .email(email)
                    .direccion(direccion)
                    .estado(true)
                    .build();
            clienteRepository.save(cliente);
            log.info(">>> [cliente-ms] Creado cliente inicial: {} (Doc: {})", nombre, documento);
        } else {
            cliente.setNombre(nombre);
            cliente.setTelefono(telefono);
            cliente.setEmail(email);
            cliente.setDireccion(direccion);
            cliente.setEstado(true);
            clienteRepository.save(cliente);
        }
    }
}

