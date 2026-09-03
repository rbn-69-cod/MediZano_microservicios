package com.medizano.facturacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashRegisterReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalBills;
    private Integer totalPayments;
    private BigDecimal subtotal;
    private BigDecimal totalGst;
    private BigDecimal totalSales;
    private BigDecimal totalCash;
    private BigDecimal totalPayPal;
    private BigDecimal totalMercadoPago;
    private BigDecimal totalCollected;
    private BigDecimal roundingAdjustment;
    private List<CashierBreakdown> cashierBreakdown;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CashierBreakdown {
        private Long cashierId;
        private String cashierName;
        private Integer billCount;
        private BigDecimal totalAmount;
    }
}

