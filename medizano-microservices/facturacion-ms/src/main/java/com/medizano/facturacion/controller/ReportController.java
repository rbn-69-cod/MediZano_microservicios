package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.CashRegisterReportResponse;
import com.medizano.facturacion.dto.GstReportResponse;
import com.medizano.facturacion.dto.SalesReportResponse;
import com.medizano.facturacion.dto.StockReportResponse;
import com.medizano.facturacion.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes Administrativos", description = "Reportes gerenciales de ventas consolidadas, arqueo de caja por turno, impuestos IGV y valorización de inventario")
public class ReportController {

    private final ReportingService reportingService;

    @GetMapping("/sales")
    @Operation(summary = "Reporte de ventas por rango de fechas", description = "Genera estadísticas consolidadas de ventas (importe total, número de ventas, ticket promedio) dentro de un rango de fechas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de ventas generado exitosamente", content = @Content(schema = @Schema(implementation = SalesReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<SalesReportResponse> getSalesReport(
            @Parameter(description = "Fecha inicial (formato YYYY-MM-DD, por defecto hace 30 días)", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Fecha final (formato YYYY-MM-DD, por defecto hoy)", example = "2026-09-03")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getDailySalesReport(start, end));
    }

    @GetMapping("/cash-register")
    @Operation(summary = "Arqueo de caja por rango de fechas", description = "Resume los cobros realizados por método de pago (Efectivo, Tarjeta, PayPal) para el cuadre y arqueo de caja.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arqueo de caja generado", content = @Content(schema = @Schema(implementation = CashRegisterReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<CashRegisterReportResponse> getCashRegisterReport(
            @Parameter(description = "Fecha inicial del arqueo (YYYY-MM-DD)", example = "2026-09-03")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Fecha final del arqueo (YYYY-MM-DD)", example = "2026-09-03")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getCashRegisterReport(start, end));
    }

    @GetMapping("/gst")
    @Operation(summary = "Reporte tributario de IGV/GST", description = "Calcula el desglose fiscal: base imponible gravada, IGV total recaudado (18%) e importes exonerados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte fiscal generado", content = @Content(schema = @Schema(implementation = GstReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<GstReportResponse> getGstReport(
            @Parameter(description = "Fecha de inicio del periodo tributario (YYYY-MM-DD)", example = "2026-08-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Fecha de fin del periodo tributario (YYYY-MM-DD)", example = "2026-08-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getGstReport(start, end));
    }

    @GetMapping("/stock")
    @Operation(summary = "Reporte consolidado de stock", description = "Proporciona la valorización monetaria total del inventario disponible y el balance de lotes vigentes vs. expirados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte de stock generado", content = @Content(schema = @Schema(implementation = StockReportResponse.class))),
            @ApiResponse(responseCode = "401", description = "No autorizado"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado - Requiere rol ADMIN")
    })
    public ResponseEntity<StockReportResponse> getStockReport() {
        return ResponseEntity.ok(reportingService.getStockReport());
    }
}
