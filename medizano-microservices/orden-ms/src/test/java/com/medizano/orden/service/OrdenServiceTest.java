package com.medizano.orden.service;

import com.medizano.orden.client.CatalogoClient;
import com.medizano.orden.client.ClienteClient;
import com.medizano.orden.client.FacturacionClient;
import com.medizano.orden.client.InventarioClient;
import com.medizano.orden.dto.CrearOrdenRequest;
import com.medizano.orden.dto.CrearOrdenRequest.ItemOrdenRequest;
import com.medizano.orden.dto.DetalleOrdenDTO;
import com.medizano.orden.dto.OrdenDTO;
import com.medizano.orden.entity.DetalleOrden;
import com.medizano.orden.entity.Orden;
import com.medizano.orden.repository.OrdenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrdenServiceTest {

    @Mock
    private OrdenRepository ordenRepository;

    @Mock
    private CatalogoClient catalogoClient;

    @Mock
    private ClienteClient clienteClient;

    @Mock
    private InventarioClient inventarioClient;

    @Mock
    private FacturacionClient facturacionClient;

    @InjectMocks
    private OrdenService ordenService;

    private Orden sampleOrden;

    @BeforeEach
    void setUp() {
        sampleOrden = Orden.builder()
                .id(1L)
                .numeroOrden("ORD-2026-TEST-001")
                .fecha(LocalDateTime.now())
                .clienteId(1L)
                .clienteNombre("Juan Pérez")
                .usuarioId(1L)
                .usuarioNombre("Admin")
                .metodoPago("EFECTIVO")
                .subtotal(new BigDecimal("10.00"))
                .impuesto(new BigDecimal("1.80"))
                .total(new BigDecimal("11.80"))
                .estado(Orden.EstadoOrden.PAGADA)
                .detalles(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("1. Crear orden falla si la lista de items está vacía")
    void testCrearOrdenSinItems() {
        CrearOrdenRequest request = new CrearOrdenRequest();
        request.setItems(new ArrayList<>());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ordenService.crearOrden(request));
        assertTrue(ex.getMessage().contains("debe contener al menos un producto"));
    }

    @Test
    @DisplayName("2. Obtener orden por ID inexistente lanza excepción")
    void testObtenerOrdenInexistente() {
        when(ordenRepository.findById(999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> ordenService.obtenerPorId(999L));
        assertTrue(ex.getMessage().contains("No se encontró la orden"));
    }

    @Test
    @DisplayName("3. Idempotencia en confirmar pago: si ya está PAGADA, no descuenta stock de nuevo")
    void testConfirmarPagoIdempotente() {
        sampleOrden.setEstado(Orden.EstadoOrden.PAGADA);
        sampleOrden.setReferenciaPago("PAYPAL-REF-123");
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(sampleOrden));

        OrdenDTO response = ordenService.confirmarPagoOrden(1L, "PAYPAL-REF-123");

        assertNotNull(response);
        assertEquals(Orden.EstadoOrden.PAGADA, response.getEstado());
        verify(inventarioClient, never()).descontarStockVenta(any());
        verify(facturacionClient, never()).generarFactura(any());
    }

    @Test
    @DisplayName("4. No permite confirmar pago de una orden cancelada")
    void testConfirmarPagoOrdenCancelada() {
        sampleOrden.setEstado(Orden.EstadoOrden.CANCELADA);
        when(ordenRepository.findById(1L)).thenReturn(Optional.of(sampleOrden));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> ordenService.confirmarPagoOrden(1L, "REF-123"));
        assertTrue(ex.getMessage().contains("cancelada"));
    }
}
