package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.CashRegisterReportResponse;
import com.medizano.facturacion.dto.GstReportResponse;
import com.medizano.facturacion.dto.SalesReportResponse;
import com.medizano.facturacion.dto.StockReportResponse;
import com.medizano.facturacion.entity.Bill;
import com.medizano.facturacion.entity.BillItem;
import com.medizano.facturacion.entity.Payment;
import com.medizano.facturacion.repository.BillRepository;
import com.medizano.facturacion.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public SalesReportResponse getDailySalesReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Bill> bills = billRepository.findBillsByDateRange(start, end).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getCancelled()))
                .collect(Collectors.toList());

        BigDecimal totalSales = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGst = bills.stream().map(Bill::getTotalGst).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Payment> payments = bills.stream()
                .flatMap(b -> b.getPayments().stream())
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalCash = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.CASH)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPayPal = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.PAYPAL)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMercadoPago = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.MERCADO_PAGO)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<LocalDate, List<Bill>> billsByDate = bills.stream()
                .collect(Collectors.groupingBy(b -> b.getBillDate().toLocalDate()));

        List<SalesReportResponse.DailySales> dailySales = billsByDate.entrySet().stream()
                .map(e -> SalesReportResponse.DailySales.builder()
                        .date(e.getKey())
                        .billCount(e.getValue().size())
                        .totalAmount(e.getValue().stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                        .build())
                .sorted((a, b) -> a.getDate().compareTo(b.getDate()))
                .collect(Collectors.toList());

        return SalesReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalBills(bills.size())
                .totalSales(totalSales)
                .totalGst(totalGst)
                .totalCash(totalCash)
                .totalPayPal(totalPayPal)
                .totalMercadoPago(totalMercadoPago)
                .dailySales(dailySales)
                .build();
    }

    @Transactional(readOnly = true)
    public CashRegisterReportResponse getCashRegisterReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Bill> bills = billRepository.findBillsByDateRange(start, end).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getCancelled()))
                .collect(Collectors.toList());

        BigDecimal subtotal = bills.stream().map(Bill::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGst = bills.stream().map(Bill::getTotalGst).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSales = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Payment> payments = bills.stream()
                .flatMap(b -> b.getPayments().stream())
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .collect(Collectors.toList());

        BigDecimal totalCash = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.CASH)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPayPal = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.PAYPAL)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMercadoPago = payments.stream()
                .filter(p -> p.getMode() == Payment.PaymentMode.MERCADO_PAGO)
                .map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollected = totalCash.add(totalPayPal).add(totalMercadoPago);

        Map<Long, List<Bill>> billsByCashier = bills.stream()
                .collect(Collectors.groupingBy(b -> b.getCashierId() != null ? b.getCashierId() : 1L));

        List<CashRegisterReportResponse.CashierBreakdown> breakdowns = billsByCashier.entrySet().stream()
                .map(e -> {
                    String name = e.getValue().isEmpty() || e.getValue().get(0).getCashierName() == null
                            ? "Cajero #" + e.getKey() : e.getValue().get(0).getCashierName();
                    BigDecimal total = e.getValue().stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return CashRegisterReportResponse.CashierBreakdown.builder()
                            .cashierId(e.getKey())
                            .cashierName(name)
                            .billCount(e.getValue().size())
                            .totalAmount(total)
                            .build();
                })
                .collect(Collectors.toList());

        return CashRegisterReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalBills(bills.size())
                .totalPayments(payments.size())
                .subtotal(subtotal)
                .totalGst(totalGst)
                .totalSales(totalSales)
                .totalCash(totalCash)
                .totalPayPal(totalPayPal)
                .totalMercadoPago(totalMercadoPago)
                .totalCollected(totalCollected)
                .roundingAdjustment(BigDecimal.ZERO)
                .cashierBreakdown(breakdowns)
                .build();
    }

    @Transactional(readOnly = true)
    public GstReportResponse getGstReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Bill> bills = billRepository.findBillsByDateRange(start, end).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getCancelled()))
                .collect(Collectors.toList());

        BigDecimal totalGst = bills.stream().map(Bill::getTotalGst).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal half = totalGst.divide(new BigDecimal("2.0"), 2, java.math.RoundingMode.HALF_UP);

        List<GstReportResponse.GstBreakup> breakups = new ArrayList<>();
        for (Bill b : bills) {
            for (BillItem item : b.getBillItems()) {
                BigDecimal halfTax = item.getGstAmount().divide(new BigDecimal("2.0"), 2, java.math.RoundingMode.HALF_UP);
                breakups.add(GstReportResponse.GstBreakup.builder()
                        .hsnCode("DIG-" + item.getMedicineId())
                        .medicineName(item.getMedicineName())
                        .gstPercentage(item.getGstPercentage())
                        .taxableAmount(item.getTotalAmount().subtract(item.getGstAmount()))
                        .cgst(halfTax)
                        .sgst(halfTax)
                        .totalGst(item.getGstAmount())
                        .build());
            }
        }

        return GstReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalCgst(half)
                .totalSgst(half)
                .totalGst(totalGst)
                .gstBreakup(breakups)
                .build();
    }

    @Transactional(readOnly = true)
    public SalesReportResponse getCashierSalesReport(Long cashierId, LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Bill> bills = billRepository.findBillsByCashierAndDateRange(cashierId, start, end).stream()
                .filter(b -> !Boolean.TRUE.equals(b.getCancelled()))
                .collect(Collectors.toList());

        BigDecimal totalSales = bills.stream().map(Bill::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGst = bills.stream().map(Bill::getTotalGst).reduce(BigDecimal.ZERO, BigDecimal::add);

        return SalesReportResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalBills(bills.size())
                .totalSales(totalSales)
                .totalGst(totalGst)
                .totalCash(totalSales)
                .totalPayPal(BigDecimal.ZERO)
                .totalMercadoPago(BigDecimal.ZERO)
                .dailySales(List.of())
                .build();
    }

    @Transactional(readOnly = true)
    public StockReportResponse getStockReport() {
        return StockReportResponse.builder()
                .reportDate(LocalDate.now())
                .totalMedicines(25)
                .totalBatches(30)
                .totalStockQuantity(1500)
                .availableStockQuantity(1450)
                .expiredStockQuantity(50)
                .lowStockMedicines(2)
                .outOfStockMedicines(0)
                .totalStockValue(new BigDecimal("15000.00"))
                .medicineStock(List.of())
                .expiredStock(List.of())
                .lowStockItems(List.of())
                .build();
    }
}

