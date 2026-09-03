package com.medizano.pago.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.MercadoPagoProperties;
import com.medizano.pago.dto.*;
import com.medizano.pago.entity.Pago;
import com.medizano.pago.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MercadoPagoPaymentServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private OrdenClient ordenClient;

    private MercadoPagoProperties mpProperties;
    private ObjectMapper objectMapper;
    private MercadoPagoPaymentService mpService;

    @BeforeEach
    void setUp() {
        mpProperties = new MercadoPagoProperties();
        mpProperties.setBaseUrl("https://api.mercadopago.com");
        mpProperties.setAccessToken("TEST_MP_ACCESS_TOKEN_12345");
        mpProperties.setPublicKey("TEST_MP_PUBLIC_KEY_67890");
        mpProperties.setCurrency("PEN");

        objectMapper = new ObjectMapper();
        mpService = new MercadoPagoPaymentService(pagoRepository, ordenClient, mpProperties, objectMapper);
    }

    @Test
    @DisplayName("1. Error al crear preferencia para orden inexistente")
    void testCrearPreferenciaOrdenInexistente() {
        when(ordenClient.obtenerOrdenPorId(999L)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            mpService.crearPreferencia(MercadoPagoPreferenceRequest.builder().ordenId(999L).build());
        });
        assertTrue(ex.getMessage().contains("No se encontró la orden"));
    }

    @Test
    @DisplayName("2. Error al crear preferencia para orden ya PAGADA")
    void testCrearPreferenciaOrdenYaPagada() {
        OrdenClient.OrdenResponse ordenPagada = new OrdenClient.OrdenResponse();
        ordenPagada.setId(100L);
        ordenPagada.setNumeroOrden("ORD-2026-MP-001");
        ordenPagada.setTotal(new BigDecimal("150.00"));
        ordenPagada.setEstado("PAGADA");

        when(ordenClient.obtenerOrdenPorId(100L)).thenReturn(ordenPagada);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            mpService.crearPreferencia(MercadoPagoPreferenceRequest.builder().ordenId(100L).build());
        });
        assertTrue(ex.getMessage().contains("ya ha sido pagada previamente"));
    }

    @Test
    @DisplayName("3. Error al crear preferencia sin Access Token de Mercado Pago")
    void testCrearPreferenciaSinAccessToken() {
        mpProperties.setAccessToken("");

        OrdenClient.OrdenResponse orden = new OrdenClient.OrdenResponse();
        orden.setId(101L);
        orden.setNumeroOrden("ORD-2026-MP-002");
        orden.setTotal(new BigDecimal("80.00"));
        orden.setEstado("PENDING");

        when(ordenClient.obtenerOrdenPorId(101L)).thenReturn(orden);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            mpService.crearPreferencia(MercadoPagoPreferenceRequest.builder().ordenId(101L).build());
        });
        assertTrue(ex.getMessage().contains("Mercado Pago no está configurado"));
    }

    @Test
    @DisplayName("4. Idempotencia en verificación: Si el pago ya fue aprobado, retorna sin duplicar")
    void testVerificarPagoIdempotenteYaAprobado() {
        Pago pagoAprobado = Pago.builder()
                .id(5L)
                .ordenId(300L)
                .numeroOrden("ORD-300")
                .mpPaymentId("MP_PAY_APPROVED_123")
                .mpPreferenceId("PREF_123")
                .amount(new BigDecimal("50.00"))
                .currency("PEN")
                .provider("MERCADO_PAGO")
                .status(Pago.EstadoPago.APPROVED)
                .externalStatus("accredited")
                .paymentDate(LocalDateTime.now())
                .build();

        when(pagoRepository.findByMpPaymentId("MP_PAY_APPROVED_123"))
                .thenReturn(Optional.of(pagoAprobado));

        MercadoPagoPaymentResponse response = mpService.verificarYConfirmarPago(
                MercadoPagoVerifyRequest.builder()
                        .paymentId("MP_PAY_APPROVED_123")
                        .ordenId(300L)
                        .build()
        );

        assertNotNull(response);
        assertEquals("approved", response.getStatus());
        assertEquals("MP_PAY_APPROVED_123", response.getPaymentId());
        assertEquals(300L, response.getOrdenId());

        verify(ordenClient, never()).confirmarPagoOrden(anyLong(), anyString());
    }

    @Test
    @DisplayName("5. Error al verificar con Payment ID vacío")
    void testVerificarPagoSinPaymentId() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            mpService.verificarYConfirmarPago(MercadoPagoVerifyRequest.builder().paymentId("").build());
        });
        assertTrue(ex.getMessage().contains("El Payment ID es obligatorio"));
    }
}

