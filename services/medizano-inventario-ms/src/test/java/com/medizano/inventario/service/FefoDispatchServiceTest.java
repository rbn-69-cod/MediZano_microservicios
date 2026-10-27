package com.medizano.inventario.service;

import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.exception.LoteNoAptoVentaException;
import com.medizano.inventario.repository.BatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FefoDispatchServiceTest {

    @Mock
    private BatchRepository batchRepository;

    @InjectMocks
    private FefoDispatchService fefoDispatchService;

    private Batch loteApto1;
    private Batch loteApto2;
    private Batch loteAlertaCanje;
    private Batch loteCritico;

    @BeforeEach
    void setUp() {
        LocalDate hoy = LocalDate.now();

        loteApto1 = Batch.builder()
                .id(1L)
                .medicineId(10L)
                .batchNumber("LOT-APTO-1")
                .expiryDate(hoy.plusDays(40))
                .quantityAvailable(10)
                .estado(EstadoLote.DISPONIBLE)
                .purchasePrice(new BigDecimal("2.0"))
                .sellingPrice(new BigDecimal("5.0"))
                .build();

        loteApto2 = Batch.builder()
                .id(2L)
                .medicineId(10L)
                .batchNumber("LOT-APTO-2")
                .expiryDate(hoy.plusDays(90))
                .quantityAvailable(20)
                .estado(EstadoLote.DISPONIBLE)
                .purchasePrice(new BigDecimal("2.0"))
                .sellingPrice(new BigDecimal("5.0"))
                .build();

        loteAlertaCanje = Batch.builder()
                .id(3L)
                .medicineId(10L)
                .batchNumber("LOT-CANJE-28D")
                .expiryDate(hoy.plusDays(28))
                .quantityAvailable(15)
                .estado(EstadoLote.ALERTA_CANJE)
                .purchasePrice(new BigDecimal("2.0"))
                .sellingPrice(new BigDecimal("5.0"))
                .build();

        loteCritico = Batch.builder()
                .id(4L)
                .medicineId(10L)
                .batchNumber("LOT-CRITICO-20D")
                .expiryDate(hoy.plusDays(20))
                .quantityAvailable(30)
                .estado(EstadoLote.RETIRAR_MOSTRADOR)
                .purchasePrice(new BigDecimal("2.0"))
                .sellingPrice(new BigDecimal("5.0"))
                .build();
    }

    @Test
    @DisplayName("FEFO: Despacha stock en estricto orden FEFO (fecha más próxima primero)")
    void testDespacharPorFefo_Exitoso() {
        when(batchRepository.findFefoEligibleBatchesForUpdate(eq(10L), any(LocalDate.class)))
                .thenReturn(List.of(loteApto1, loteApto2));

        List<FefoDispatchService.DespachoLoteResultado> resultado = fefoDispatchService.despacharPorFefo(10L, 15);

        assertNotNull(resultado);
        assertEquals(2, resultado.size());
        assertEquals("LOT-APTO-1", resultado.get(0).getBatchNumber());
        assertEquals(10, resultado.get(0).getCantidadDescontada());
        assertEquals(0, loteApto1.getQuantityAvailable());

        assertEquals("LOT-APTO-2", resultado.get(1).getBatchNumber());
        assertEquals(5, resultado.get(1).getCantidadDescontada());
        assertEquals(15, loteApto2.getQuantityAvailable());

        verify(batchRepository, times(2)).save(any(Batch.class));
    }

    @Test
    @DisplayName("FEFO: Lote en ALERTA_CANJE con más de 25 días es apto para venta")
    void testDespacharPorFefo_AlertaCanjePermitido() {
        when(batchRepository.findFefoEligibleBatchesForUpdate(eq(10L), any(LocalDate.class)))
                .thenReturn(List.of(loteAlertaCanje));

        List<FefoDispatchService.DespachoLoteResultado> resultado = fefoDispatchService.despacharPorFefo(10L, 5);

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("LOT-CANJE-28D", resultado.get(0).getBatchNumber());
        assertEquals(5, resultado.get(0).getCantidadDescontada());
        assertEquals(10, loteAlertaCanje.getQuantityAvailable());
    }

    @Test
    @DisplayName("POS Guard: Lanza LoteNoAptoVentaException (HTTP 422) si el stock físico está en ventana crítica (<= 25 días)")
    void testDespacharPorFefo_BloqueoVentaCritica() {
        when(batchRepository.findFefoEligibleBatchesForUpdate(eq(10L), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(batchRepository.findByMedicineIdForUpdate(10L))
                .thenReturn(List.of(loteCritico));

        LoteNoAptoVentaException ex = assertThrows(LoteNoAptoVentaException.class, () ->
                fefoDispatchService.despacharPorFefo(10L, 5)
        );

        assertTrue(ex.getMessage().contains("Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica"));
    }

    @Test
    @DisplayName("POS Guard: Despacho de lote específico en RETIRAR_MOSTRADOR es bloqueado con HTTP 422")
    void testDespacharLoteEspecifico_Bloqueado() {
        when(batchRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(loteCritico));

        LoteNoAptoVentaException ex = assertThrows(LoteNoAptoVentaException.class, () ->
                fefoDispatchService.despacharLoteEspecifico(10L, 4L, 5)
        );

        assertTrue(ex.getMessage().contains("Venta bloqueada"));
    }
}

