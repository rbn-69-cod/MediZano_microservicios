package com.medizano.inventario.service;

import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.exception.LoteNoAptoVentaException;
import com.medizano.inventario.repository.BatchRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FefoDispatchService {

    private final BatchRepository batchRepository;

    public static final int DIAS_BLOQUEO_VENTA = 25;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DespachoLoteResultado {
        private Long batchId;
        private String batchNumber;
        private int cantidadDescontada;
    }

    /**
     * Valida y despacha stock de un lote específico seleccionado manualmente.
     * Aplica guard de bloqueo si el lote está en ventana crítica (<= 25 días) o en cuarentena.
     */
    @Transactional
    public DespachoLoteResultado despacharLoteEspecifico(Long medicineId, Long batchId, int cantidad) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad a descontar debe ser mayor a 0");
        }

        Batch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Lote no encontrado con ID: " + batchId));

        if (!batch.getMedicineId().equals(medicineId)) {
            throw new IllegalArgumentException("El lote " + batch.getBatchNumber() + " no pertenece al medicamento ID " + medicineId);
        }

        LocalDate fechaLimiteSeguridad = LocalDate.now().plusDays(DIAS_BLOQUEO_VENTA);

        // Guard de negocio: Verificar si el lote se encuentra en cuarentena, baja o <= 25 días
        boolean estadoNoPermitido = batch.getEstado() == EstadoLote.RETIRAR_MOSTRADOR 
                || batch.getEstado() == EstadoLote.VENCIDO_BAJA 
                || batch.getEstado() == EstadoLote.DEVUELTO;
        boolean enVentanaCritica = batch.getExpiryDate() == null || !batch.getExpiryDate().isAfter(fechaLimiteSeguridad);

        if (estadoNoPermitido || enVentanaCritica) {
            log.warn("Bloqueo de venta activado para lote {}: estado={}, vencimiento={}",
                    batch.getBatchNumber(), batch.getEstado(), batch.getExpiryDate());
            throw new LoteNoAptoVentaException(
                    "Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica (<= 25 días) o en cuarentena. Trasladar producto a área de bajas."
            );
        }

        if (batch.getQuantityAvailable() < cantidad) {
            throw new IllegalStateException("Stock insuficiente en el lote " + batch.getBatchNumber() +
                    ". Disponible: " + batch.getQuantityAvailable() + ", Solicitado: " + cantidad);
        }

        batch.setQuantityAvailable(batch.getQuantityAvailable() - cantidad);
        batchRepository.save(batch);

        return DespachoLoteResultado.builder()
                .batchId(batch.getId())
                .batchNumber(batch.getBatchNumber())
                .cantidadDescontada(cantidad)
                .build();
    }

    /**
     * Algoritmo FEFO estricto: Despacha stock priorizando el lote con fecha de caducidad más próxima.
     * Solo toma lotes en estado DISPONIBLE o ALERTA_CANJE con vencimiento superior a 25 días.
     * Si no hay stock apto pero existen lotes vencidos o en ventana crítica, lanza LoteNoAptoVentaException (HTTP 422).
     */
    @Transactional
    public List<DespachoLoteResultado> despacharPorFefo(Long medicineId, int cantidadTotalRequerida) {
        if (cantidadTotalRequerida <= 0) {
            throw new IllegalArgumentException("La cantidad total requerida debe ser mayor a 0");
        }

        LocalDate fechaLimiteSeguridad = LocalDate.now().plusDays(DIAS_BLOQUEO_VENTA);

        // Consulta optimizada con bloqueo pesimista PESSIMISTIC_WRITE
        List<Batch> lotesAptos = batchRepository.findFefoEligibleBatchesForUpdate(medicineId, fechaLimiteSeguridad);

        int totalStockApto = lotesAptos.stream()
                .mapToInt(b -> b.getQuantityAvailable() != null ? b.getQuantityAvailable() : 0)
                .sum();

        if (lotesAptos.isEmpty() || totalStockApto < cantidadTotalRequerida) {
            // Verificar si el producto tiene existencias pero todas están bloqueadas por vencimiento/cuarentena
            List<Batch> todosLosLotes = batchRepository.findByMedicineIdForUpdate(medicineId);
            int stockTotalFisico = todosLosLotes.stream()
                    .mapToInt(b -> b.getQuantityAvailable() != null ? b.getQuantityAvailable() : 0)
                    .sum();

            if (stockTotalFisico > 0 && totalStockApto == 0) {
                log.warn("Venta bloqueada para producto ID {}: existen {} unidades físicas pero todas están en ventana crítica (<= 25 días) o cuarentena.",
                        medicineId, stockTotalFisico);
                throw new LoteNoAptoVentaException(
                        "Venta bloqueada: El stock disponible para este producto se encuentra en ventana crítica (<= 25 días) o en cuarentena. Trasladar producto a área de bajas."
                );
            }

            throw new IllegalStateException("Stock apto insuficiente para el producto ID " + medicineId +
                    ". Stock apto disponible: " + totalStockApto + ", Requerido: " + cantidadTotalRequerida);
        }

        List<DespachoLoteResultado> resultados = new ArrayList<>();
        int pendiente = cantidadTotalRequerida;

        for (Batch lote : lotesAptos) {
            if (pendiente <= 0) break;
            int disponible = lote.getQuantityAvailable() != null ? lote.getQuantityAvailable() : 0;
            if (disponible <= 0) continue;

            int aDescontar = Math.min(disponible, pendiente);
            lote.setQuantityAvailable(disponible - aDescontar);
            batchRepository.save(lote);

            resultados.add(DespachoLoteResultado.builder()
                    .batchId(lote.getId())
                    .batchNumber(lote.getBatchNumber())
                    .cantidadDescontada(aDescontar)
                    .build());

            pendiente -= aDescontar;
            log.info("Despacho FEFO aplicado: Lote={}, Descontado={}, RestanteEnLote={}",
                    lote.getBatchNumber(), aDescontar, lote.getQuantityAvailable());
        }

        return resultados;
    }
}

