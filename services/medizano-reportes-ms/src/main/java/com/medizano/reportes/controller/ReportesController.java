package com.medizano.reportes.controller;

import com.medizano.reportes.client.FacturacionReportsClient;
import com.medizano.reportes.dto.CashRegisterReportResponse;
import com.medizano.reportes.dto.GstReportResponse;
import com.medizano.reportes.dto.SalesReportResponse;
import com.medizano.reportes.dto.StockReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportesController {
    private final FacturacionReportsClient reportsClient;

    @GetMapping("/sales")
    public SalesReportResponse sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportsClient.sales(startDate, endDate);
    }

    @GetMapping("/cash-register")
    public CashRegisterReportResponse cashRegister(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportsClient.cashRegister(startDate, endDate);
    }

    @GetMapping("/gst")
    public GstReportResponse gst(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportsClient.gst(startDate, endDate);
    }

    @GetMapping("/stock")
    public StockReportResponse stock() {
        return reportsClient.stock();
    }
}
