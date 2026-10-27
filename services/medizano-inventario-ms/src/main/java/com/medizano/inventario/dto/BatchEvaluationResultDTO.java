package com.medizano.inventario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Resultado consolidado de la evaluación de vencimientos FEFO")
public class BatchEvaluationResultDTO {

    private LocalDateTime fechaEvaluacion;
    private int totalLotesEvaluados;
    private int lotesVencidosBaja;
    private int lotesRetirarMostrador;
    private int lotesAlertaCanje;
    private int totalProveedoresNotificados;
    private List<String> detalles;
}

