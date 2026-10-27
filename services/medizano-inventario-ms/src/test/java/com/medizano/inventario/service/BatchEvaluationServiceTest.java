package com.medizano.inventario.service;

import com.medizano.inventario.client.ProveedorClient;
import com.medizano.inventario.client.dto.ProveedorDTO;
import com.medizano.inventario.dto.BatchEvaluationResultDTO;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchEvaluationServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ProveedorClient proveedorClient;

    @Mock
    private EmailNotificationService emailNotificationService;

    @InjectMocks
    private BatchEvaluationService batchEvaluationService;

    private Batch batchExpirado;
    private Batch batchRetirar;
    private Batch batchCanje;

    @BeforeEach
    void setUp() {
        LocalDate hoy = LocalDate.now();

        batchExpirado = Batch.builder()
                .id(1L)
                .batchNumber("LOT-EXP")
                .medicineId(100L)
                .expiryDate(hoy.minusDays(1))
                .quantityAvailable(10)
                .estado(EstadoLote.DISPONIBLE)
                .proveedorId(10L)
                .build();

        batchRetirar = Batch.builder()
                .id(2L)
                .batchNumber("LOT-RETIRAR")
                .medicineId(101L)
                .expiryDate(hoy.plusDays(15))
                .quantityAvailable(20)
                .estado(EstadoLote.DISPONIBLE)
                .proveedorId(10L)
                .build();

        batchCanje = Batch.builder()
                .id(3L)
                .batchNumber("LOT-CANJE")
                .medicineId(102L)
                .expiryDate(hoy.plusDays(28))
                .quantityAvailable(30)
                .estado(EstadoLote.DISPONIBLE)
                .proveedorId(20L)
                .numeroFacturaCompra("F001-9988")
                .build();
    }

    @Test
    @DisplayName("Evaluación Cron: Aplica transiciones de estado según días restantes y emite alerta por correo al proveedor")
    void testEjecutarEvaluacionVencimientos() {
        when(batchRepository.findBatchesForEvaluation())
                .thenReturn(List.of(batchExpirado, batchRetirar, batchCanje));

        ProveedorDTO proveedor20 = ProveedorDTO.builder()
                .id(20L)
                .razonSocial("Laboratorios Bagó del Perú")
                .emailContacto("contacto@bago.com.pe")
                .ruc("20100045678")
                .build();

        when(proveedorClient.obtenerProveedoresPorIds(Set.of(20L)))
                .thenReturn(Map.of(20L, proveedor20));

        BatchEvaluationResultDTO resultado = batchEvaluationService.ejecutarEvaluacionVencimientos();

        assertNotNull(resultado);
        assertEquals(3, resultado.getTotalLotesEvaluados());
        assertEquals(1, resultado.getLotesVencidosBaja());
        assertEquals(1, resultado.getLotesRetirarMostrador());
        assertEquals(1, resultado.getLotesAlertaCanje());
        assertEquals(1, resultado.getTotalProveedoresNotificados());

        assertEquals(EstadoLote.VENCIDO_BAJA, batchExpirado.getEstado());
        assertEquals(EstadoLote.RETIRAR_MOSTRADOR, batchRetirar.getEstado());
        assertEquals(EstadoLote.ALERTA_CANJE, batchCanje.getEstado());

        verify(batchRepository).saveAll(any());
        verify(emailNotificationService).enviarAlertaCanje(eq(proveedor20), any());
    }
}

