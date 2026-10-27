package com.medizano.inventario.controller;

import com.medizano.inventario.dto.BatchEvaluationResultDTO;
import com.medizano.inventario.service.BatchEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/inventario/batches", "/api/v1/inventario/batches"})
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Evaluación de Vencimientos FEFO", description = "Endpoints administrativos para control de caducidades, alertas de canje y ejecución manual de evaluación de lotes")
public class BatchEvaluationController {

    private final BatchEvaluationService batchEvaluationService;

    @PostMapping("/evaluar-vencimientos-manual")
    @Operation(
            summary = "Disparar evaluación manual de vencimientos y alertas por correo",
            description = "Ejecuta inmediatamente el algoritmo FEFO de clasificación de lotes (ALERTA_CANJE, RETIRAR_MOSTRADOR, VENCIDO_BAJA) y emite notificaciones por correo electrónico a proveedores sin esperar la medianoche."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evaluación completada exitosamente"),
            @ApiResponse(responseCode = "500", description = "Error interno del servidor durante la evaluación")
    })
    public ResponseEntity<BatchEvaluationResultDTO> dispararEvaluacionManual() {
        log.info("Disparando manualmente evaluación de vencimientos FEFO...");
        BatchEvaluationResultDTO resultado = batchEvaluationService.ejecutarEvaluacionVencimientos();
        return ResponseEntity.ok(resultado);
    }
}

