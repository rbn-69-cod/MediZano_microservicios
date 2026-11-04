package com.medizano.reportes.client;

import com.medizano.reportes.dto.CashRegisterReportResponse;
import com.medizano.reportes.dto.GstReportResponse;
import com.medizano.reportes.dto.SalesReportResponse;
import com.medizano.reportes.dto.StockReportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@FeignClient(name = "facturacion-ms")
public interface FacturacionReportsClient {
    @GetMapping("/api/admin/reports/sales")
    SalesReportResponse sales(@RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                              @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    @GetMapping("/api/admin/reports/cash-register")
    CashRegisterReportResponse cashRegister(@RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    @GetMapping("/api/admin/reports/gst")
    GstReportResponse gst(@RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                          @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    @GetMapping("/api/admin/reports/stock")
    StockReportResponse stock();
}
