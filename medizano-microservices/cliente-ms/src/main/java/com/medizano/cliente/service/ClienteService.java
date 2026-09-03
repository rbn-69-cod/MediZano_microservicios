package com.medizano.cliente.service;

import com.medizano.cliente.dto.ClienteDTO;
import com.medizano.cliente.dto.ClienteRequest;
import com.medizano.cliente.entity.Cliente;
import com.medizano.cliente.repository.ClienteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClienteService {

    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarTodos() {
        return clienteRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClienteDTO> listarActivos() {
        return clienteRepository.findByEstadoTrue().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorId(Long id) {
        Cliente c = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el cliente con ID: " + id));
        return mapToDTO(c);
    }

    @Transactional(readOnly = true)
    public ClienteDTO buscarPorDocumento(String documento) {
        Cliente c = clienteRepository.findByDocumento(documento)
                .orElseThrow(() -> new RuntimeException("No se encontró el cliente con documento: " + documento));
        return mapToDTO(c);
    }

    @Transactional
    public ClienteDTO crear(ClienteRequest request) {
        if (request.getNombre() == null || request.getNombre().trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }

        Cliente cliente = Cliente.builder()
                .nombre(request.getNombre().trim())
                .documento(request.getDocumento() != null ? request.getDocumento().trim() : null)
                .telefono(request.getTelefono() != null ? request.getTelefono().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .direccion(request.getDireccion() != null ? request.getDireccion().trim() : null)
                .estado(request.getEstado() != null ? request.getEstado() : true)
                .build();

        cliente = clienteRepository.save(cliente);
        log.info("Cliente registrado con ID: {}", cliente.getId());
        return mapToDTO(cliente);
    }

    @Transactional
    public ClienteDTO actualizar(Long id, ClienteRequest request) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró el cliente con ID: " + id));

        if (request.getNombre() != null && !request.getNombre().trim().isEmpty()) {
            cliente.setNombre(request.getNombre().trim());
        }
        if (request.getDocumento() != null) {
            cliente.setDocumento(request.getDocumento().trim());
        }
        if (request.getTelefono() != null) {
            cliente.setTelefono(request.getTelefono().trim());
        }
        if (request.getEmail() != null) {
            cliente.setEmail(request.getEmail().trim());
        }
        if (request.getDireccion() != null) {
            cliente.setDireccion(request.getDireccion().trim());
        }
        if (request.getEstado() != null) {
            cliente.setEstado(request.getEstado());
        }

        cliente = clienteRepository.save(cliente);
        log.info("Cliente actualizado con ID: {}", cliente.getId());
        return mapToDTO(cliente);
    }

    @Transactional
    public void eliminar(Long id) {
        if (!clienteRepository.existsById(id)) {
            throw new RuntimeException("No se encontró el cliente con ID: " + id);
        }
        clienteRepository.deleteById(id);
        log.info("Cliente eliminado con ID: {}", id);
    }

    private ClienteDTO mapToDTO(Cliente c) {
        return ClienteDTO.builder()
                .id(c.getId())
                .nombre(c.getNombre())
                .documento(c.getDocumento())
                .telefono(c.getTelefono())
                .email(c.getEmail())
                .direccion(c.getDireccion())
                .estado(c.getEstado())
                .createdAt(c.getCreatedAt())
                .build();
    }
}

