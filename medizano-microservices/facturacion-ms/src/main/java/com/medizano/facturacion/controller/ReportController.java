package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.CashRegisterReportResponse;
import com.medizano.facturacion.dto.GstReportResponse;
import com.medizano.facturacion.dto.SalesReportResponse;
import com.medizano.facturacion.dto.StockReportResponse;
import com.medizano.facturacion.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Reportes Administrativos", description = "Reportes de ventas, arqueo de caja, impuestos y stock")
public class ReportController {

    private final ReportingService reportingService;

    @GetMapping("/sales")
    @Operation(summary = "Reporte de ventas por rango de fechas")
    public ResponseEntity<SalesReportResponse> getSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getDailySalesReport(start, end));
    }

    @GetMapping("/cash-register")
    @Operation(summary = "Arqueo de caja por rango de fechas")
    public ResponseEntity<CashRegisterReportResponse> getCashRegisterReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getCashRegisterReport(start, end));
    }

    @GetMapping("/gst")
    @Operation(summary = "Reporte tributario de IGV/GST")
    public ResponseEntity<GstReportResponse> getGstReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        return ResponseEntity.ok(reportingService.getGstReport(start, end));
    }

    @GetMapping("/stock")
    @Operation(summary = "Reporte consolidado de stock")
    public ResponseEntity<StockReportResponse> getStockReport() {
        return ResponseEntity.ok(reportingService.getStockReport());
    }
}
