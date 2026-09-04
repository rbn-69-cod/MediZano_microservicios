package com.medizano.pago.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medizano.pago.client.OrdenClient;
import com.medizano.pago.config.PayPalProperties;
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
class PayPalServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private OrdenClient ordenClient;

    private PayPalProperties payPalProperties;
    private ObjectMapper objectMapper;
    private PayPalService payPalService;

    @BeforeEach
    void setUp() {
        payPalProperties = new PayPalProperties();
        payPalProperties.setBaseUrl("https://api-m.sandbox.paypal.com");
        payPalProperties.setClientId("TEST_PAYPAL_CLIENT_ID_12345");
        payPalProperties.setClientSecret("TEST_PAYPAL_SECRET_67890");
        payPalProperties.setCurrency("USD");
        payPalProperties.setExchangeRate(new BigDecimal("3.75"));

        objectMapper = new ObjectMapper();
        payPalService = new PayPalService(pagoRepository, ordenClient, payPalProperties, objectMapper);
    }

    @Test
    @DisplayName("1. Obtener configuración pública de PayPal (Client ID y Moneda)")
    void testObtenerConfiguracionPublica() {
        PayPalConfigResponse config = payPalService.obtenerConfiguracionPublica();
        assertNotNull(config);
        assertEquals("TEST_PAYPAL_CLIENT_ID_12345", config.getClientId());
        assertEquals("USD", config.getCurrency());
        assertEquals("https://api-m.sandbox.paypal.com", config.getBaseUrl());
    }

    @Test
    @DisplayName("2. Error cuando las credenciales no están configuradas")
    void testGetAccessTokenSinCredenciales() {
        payPalProperties.setClientId("");
        com.medizano.pago.exception.PaymentGatewayNotConfiguredException ex = assertThrows(
                com.medizano.pago.exception.PaymentGatewayNotConfiguredException.class, () -> payPalService.getAccessToken());
        assertTrue(ex.getMessage().contains("PayPal Sandbox no está configurado"));
    }

    @Test
    @DisplayName("3. Error al crear orden para orden interna inexistente")
    void testCrearOrdenParaOrdenInexistente() {
        when(ordenClient.obtenerOrdenPorId(999L)).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            payPalService.crearOrden(PayPalOrderRequest.builder().ordenId(999L).build());
        });
        assertTrue(ex.getMessage().contains("No se encontró la orden"));
    }

    @Test
    @DisplayName("4. Error al crear orden para orden ya PAGADA")
    void testCrearOrdenParaOrdenYaPagada() {
        OrdenClient.OrdenResponse ordenPagada = new OrdenClient.OrdenResponse();
        ordenPagada.setId(100L);
        ordenPagada.setNumeroOrden("ORD-2026-001");
        ordenPagada.setTotal(new BigDecimal("100.00"));
        ordenPagada.setEstado("PAGADA");

        when(ordenClient.obtenerOrdenPorId(100L)).thenReturn(ordenPagada);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            payPalService.crearOrden(PayPalOrderRequest.builder().ordenId(100L).build());
        });
        assertTrue(ex.getMessage().contains("ya ha sido pagada previamente"));
    }

    @Test
    @DisplayName("5. Idempotencia en captura: si el pago ya fue aprobado, retorna sin reprocesar")
    void testCapturaIdempotentePagoYaAprobado() {
        Pago pagoAprobado = Pago.builder()
                .id(1L)
                .ordenId(200L)
                .numeroOrden("ORD-2026-002")
                .paypalOrderId("PAYPAL_ORDER_APPROVED_123")
                .paypalCaptureId("CAPTURE_APPROVED_456")
                .amount(new BigDecimal("26.67"))
                .currency("USD")
                .status(Pago.EstadoPago.APPROVED)
                .paymentDate(LocalDateTime.now())
                .build();

        when(pagoRepository.findByPaypalOrderId("PAYPAL_ORDER_APPROVED_123"))
                .thenReturn(Optional.of(pagoAprobado));

        PayPalCaptureResponse response = payPalService.capturarOrden("PAYPAL_ORDER_APPROVED_123");

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("CAPTURE_APPROVED_456", response.getPaypalCaptureId());
        assertEquals(200L, response.getOrdenId());

        verify(ordenClient, never()).confirmarPagoOrden(anyLong(), anyString());
    }

    @Test
    @DisplayName("6. Error al capturar orden con PayPal Order ID no registrado")
    void testCapturarOrdenNoRegistrada() {
        when(pagoRepository.findByPaypalOrderId("ORDER_NOT_FOUND"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            payPalService.capturarOrden("ORDER_NOT_FOUND");
        });
        assertTrue(ex.getMessage().contains("No se encontró el pago asociado"));
    }

    @Test
    @DisplayName("7. Consulta de pagos por orden y por ID")
    void testConsultarPagoPorId() {
        Pago pago = Pago.builder()
                .id(10L)
                .ordenId(50L)
                .numeroOrden("ORD-50")
                .amount(new BigDecimal("15.00"))
                .currency("USD")
                .status(Pago.EstadoPago.PENDING)
                .build();

        when(pagoRepository.findById(10L)).thenReturn(Optional.of(pago));

        PagoDTO dto = payPalService.obtenerPorId(10L);
        assertNotNull(dto);
        assertEquals(10L, dto.getId());
        assertEquals(50L, dto.getOrdenId());
        assertEquals(Pago.EstadoPago.PENDING, dto.getStatus());
    }
}

