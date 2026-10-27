package com.medizano.inventario.service;

import com.medizano.inventario.client.ProveedorClient;
import com.medizano.inventario.client.dto.ProveedorDTO;
import com.medizano.inventario.dto.BatchEvaluationResultDTO;
import com.medizano.inventario.entity.Batch;
import com.medizano.inventario.entity.EstadoLote;
import com.medizano.inventario.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchEvaluationService {

    private final BatchRepository batchRepository;
    private final ProveedorClient proveedorClient;
    private final EmailNotificationService emailNotificationService;

    /**
     * Tarea programada diaria ejecutada a medianoche (00:00:00).
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void evaluarVencimientosDiario() {
        log.info("Iniciando cron programado de medianoche para evaluación de lotes (FEFO)...");
        ejecutarEvaluacionVencimientos();
    }

    /**
     * Algoritmo de evaluación de vencimientos FEFO y alertas preventivas.
     * Puede ejecutarse programadamente o invocarse de forma manual desde el endpoint administrativo.
     */
    @Transactional
    public BatchEvaluationResultDTO ejecutarEvaluacionVencimientos() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();
        log.info("Ejecutando evaluación de vencimientos a fecha {}", hoy);

        List<Batch> lotesActivos = batchRepository.findBatchesForEvaluation();
        List<Batch> lotesModificados = new ArrayList<>();
        List<Batch> lotesParaNotificar = new ArrayList<>();
        List<String> detalles = new ArrayList<>();

        int contVencidos = 0;
        int contRetirarMostrador = 0;
        int contAlertaCanje = 0;

        for (Batch batch : lotesActivos) {
            long diasRestantes = ChronoUnit.DAYS.between(hoy, batch.getExpiryDate());
            EstadoLote estadoPrevio = batch.getEstado();
            boolean modificado = false;

            // Regla a: Expirado
            if (diasRestantes <= 0) {
                if (batch.getEstado() != EstadoLote.VENCIDO_BAJA) {
                    batch.setEstado(EstadoLote.VENCIDO_BAJA);
                    contVencidos++;
                    modificado = true;
                    detalles.add(String.format("Lote %s (Prod #%d): VENCIDO_BAJA (%d días)",
                            batch.getBatchNumber(), batch.getMedicineId(), diasRestantes));
                }
            }
            // Regla b: Ventana crítica de mostrador (<= 25 días y > 0)
            else if (diasRestantes <= 25) {
                if (batch.getEstado() != EstadoLote.RETIRAR_MOSTRADOR) {
                    batch.setEstado(EstadoLote.RETIRAR_MOSTRADOR);
                    contRetirarMostrador++;
                    modificado = true;
                    detalles.add(String.format("Lote %s (Prod #%d): RETIRAR_MOSTRADOR (%d días restantes)",
                            batch.getBatchNumber(), batch.getMedicineId(), diasRestantes));
                }
            }
            // Regla c: Ventana preventiva de canje (<= 30 días y > 25)
            else if (diasRestantes <= 30 && estadoPrevio == EstadoLote.DISPONIBLE) {
                batch.setEstado(EstadoLote.ALERTA_CANJE);
                lotesParaNotificar.add(batch);
                contAlertaCanje++;
                modificado = true;
                detalles.add(String.format("Lote %s (Prod #%d): ALERTA_CANJE (%d días restantes)",
                        batch.getBatchNumber(), batch.getMedicineId(), diasRestantes));
            }

            if (modificado) {
                batch.setFechaUltimaEvaluacion(ahora);
                lotesModificados.add(batch);
            }
        }

        // Persistir todos los lotes evaluados con cambio de estado
        if (!lotesModificados.isEmpty()) {
            batchRepository.saveAll(lotesModificados);
            log.info("Persistidos {} lotes con cambio de estado FEFO", lotesModificados.size());
        }

        int proveedoresNotificados = 0;

        // Si existen lotes en ventana preventiva de canje, procesar notificación consolidada
        if (!lotesParaNotificar.isEmpty()) {
            Set<Long> proveedorIds = lotesParaNotificar.stream()
                    .map(Batch::getProveedorId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            log.info("Lotes en ALERTA_CANJE detectados: {}. Proveedores únicos a consultar: {}",
                    lotesParaNotificar.size(), proveedorIds);

            if (!proveedorIds.isEmpty()) {
                Map<Long, ProveedorDTO> mapaProveedores = Collections.emptyMap();
                try {
                    mapaProveedores = proveedorClient.obtenerProveedoresPorIds(proveedorIds);
                } catch (Exception ex) {
                    log.error("Fallo al consultar proveedores vía OpenFeign (proveedor-ms): {}. Se continuará sin correo.", ex.getMessage());
                }

                Map<Long, List<Batch>> lotesPorProveedor = lotesParaNotificar.stream()
                        .filter(b -> b.getProveedorId() != null)
                        .collect(Collectors.groupingBy(Batch::getProveedorId));

                for (Map.Entry<Long, List<Batch>> entry : lotesPorProveedor.entrySet()) {
                    Long proveedorId = entry.getKey();
                    List<Batch> lotesProveedor = entry.getValue();
                    ProveedorDTO provDTO = mapaProveedores.get(proveedorId);

                    if (provDTO != null) {
                        emailNotificationService.enviarAlertaCanje(provDTO, lotesProveedor);
                        proveedoresNotificados++;
                    } else {
                        log.warn("No se encontró información para el proveedor ID: {}. Lotes sin notificar: {}",
                                proveedorId, lotesProveedor.stream().map(Batch::getBatchNumber).toList());
                    }
                }
            }
        }

        return BatchEvaluationResultDTO.builder()
                .fechaEvaluacion(ahora)
                .totalLotesEvaluados(lotesActivos.size())
                .lotesVencidosBaja(contVencidos)
                .lotesRetirarMostrador(contRetirarMostrador)
                .lotesAlertaCanje(contAlertaCanje)
                .totalProveedoresNotificados(proveedoresNotificados)
                .detalles(detalles)
                .build();
    }
}

