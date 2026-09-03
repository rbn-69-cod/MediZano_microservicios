package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.*;
import com.medizano.facturacion.entity.Bill;
import com.medizano.facturacion.entity.BillItem;
import com.medizano.facturacion.entity.Payment;
import com.medizano.facturacion.repository.BillItemRepository;
import com.medizano.facturacion.repository.BillRepository;
import com.medizano.facturacion.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final PaymentRepository paymentRepository;

    private static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    @Transactional
    public BillResponse createBill(CreateBillRequest request) {
        String billNumber = generarNumeroFactura();

        Bill bill = Bill.builder()
                .billNumber(billNumber)
                .billDate(LocalDateTime.now())
                .cashierId(1L)
                .cashierName("Cajero Principal")
                .customerName(request.getCustomerName() != null && !request.getCustomerName().trim().isEmpty() ? request.getCustomerName().trim() : "Cliente General")
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .subtotal(BigDecimal.ZERO)
                .totalGst(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .paymentStatus(Bill.PaymentStatus.PENDING)
                .billItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();

        BigDecimal subtotalAcumulado = BigDecimal.ZERO;

        for (BillItemRequest itemReq : request.getItems()) {
            Long medId = itemReq.getMedicineId() != null ? itemReq.getMedicineId() : 1L;
            String medName = "Medicamento #" + medId;
            BigDecimal unitPrice = new BigDecimal("10.00");
            BigDecimal gstPercentage = new BigDecimal("18.00");

            BigDecimal lineTotal = unitPrice.multiply(new BigDecimal(itemReq.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineNet = lineTotal.divide(BigDecimal.ONE.add(IGV_RATE), 2, RoundingMode.HALF_UP);
            BigDecimal lineGst = lineTotal.subtract(lineNet).setScale(2, RoundingMode.HALF_UP);

            subtotalAcumulado = subtotalAcumulado.add(lineTotal);

            BillItem item = BillItem.builder()
                    .bill(bill)
                    .medicineId(medId)
                    .medicineName(medName)
                    .batchId(1L)
                    .batchNumber("LOT-" + medId)
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .gstPercentage(gstPercentage)
                    .gstAmount(lineGst)
                    .totalAmount(lineTotal)
                    .build();

            bill.getBillItems().add(item);
        }

        BigDecimal total = subtotalAcumulado.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotalNeto = total.divide(BigDecimal.ONE.add(IGV_RATE), 2, RoundingMode.HALF_UP);
        BigDecimal totalIgv = total.subtract(subtotalNeto).setScale(2, RoundingMode.HALF_UP);

        bill.setSubtotal(subtotalNeto);
        bill.setTotalGst(totalIgv);
        bill.setTotalAmount(total);

        // Procesar y validar pagos con cálculo estricto de vuelto e ingreso neto
        BigDecimal totalPagadoReal = BigDecimal.ZERO;
        BigDecimal cashTenderedTotal = BigDecimal.ZERO;
        BigDecimal saldoRestante = total;

        if (request.getPayments() != null && !request.getPayments().isEmpty()) {
            java.util.Set<String> seenRefs = new java.util.HashSet<>();
            for (PaymentRequest pReq : request.getPayments()) {
                if (pReq.getAmount() == null || pReq.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("El monto del pago debe ser mayor a cero");
                }

                String ref = pReq.getPaymentReference() != null && !pReq.getPaymentReference().trim().isEmpty()
                        ? pReq.getPaymentReference().trim()
                        : "PAY-" + System.currentTimeMillis() + "-" + (new Random().nextInt(900) + 100);

                if (!seenRefs.add(ref)) {
                    throw new IllegalArgumentException("Referencia de pago duplicada en la misma transacción: " + ref);
                }

                if (pReq.getMode() == Payment.PaymentMode.PAYPAL || pReq.getMode() == Payment.PaymentMode.MERCADO_PAGO) {
                    if (pReq.getAmount().compareTo(saldoRestante) > 0) {
                        throw new IllegalArgumentException(String.format(
                                "Los pagos electrónicos no admiten sobrepago. Saldo restante: S/ %.2f, monto enviado: S/ %.2f",
                                saldoRestante, pReq.getAmount()));
                    }
                    Payment payment = Payment.builder()
                            .bill(bill)
                            .paymentReference(ref)
                            .mode(pReq.getMode())
                            .amount(pReq.getAmount())
                            .status(Payment.PaymentStatus.COMPLETED)
                            .paymentDate(LocalDateTime.now())
                            .build();
                    bill.getPayments().add(payment);
                    saldoRestante = saldoRestante.subtract(pReq.getAmount());
                    totalPagadoReal = totalPagadoReal.add(pReq.getAmount());
                } else {
                    // Pago en EFECTIVO (CASH)
                    cashTenderedTotal = cashTenderedTotal.add(pReq.getAmount());
                    BigDecimal montoAplicado = pReq.getAmount().min(saldoRestante);
                    saldoRestante = saldoRestante.subtract(montoAplicado);
                    totalPagadoReal = totalPagadoReal.add(montoAplicado);

                    Payment payment = Payment.builder()
                            .bill(bill)
                            .paymentReference(ref)
                            .mode(pReq.getMode())
                            .amount(montoAplicado) // Monto neto aplicado a la venta para arqueo de caja exacto
                            .status(Payment.PaymentStatus.COMPLETED)
                            .paymentDate(LocalDateTime.now())
                            .build();
                    bill.getPayments().add(payment);
                }
            }

            if (saldoRestante.compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalArgumentException(String.format(
                        "Pago insuficiente: El total a pagar es S/ %.2f pero solo se cubrió S/ %.2f. Faltan S/ %.2f",
                        total, totalPagadoReal, saldoRestante));
            }

            BigDecimal vuelto = cashTenderedTotal.compareTo(BigDecimal.ZERO) > 0 && cashTenderedTotal.compareTo(totalPagadoReal) > 0
                    ? cashTenderedTotal.subtract(totalPagadoReal).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            bill.setCashTendered(cashTenderedTotal.compareTo(BigDecimal.ZERO) > 0 ? cashTenderedTotal : totalPagadoReal);
            bill.setChangeAmount(vuelto);
            bill.setPaymentStatus(Bill.PaymentStatus.PAID);
        } else {
            bill.setPaymentStatus(Bill.PaymentStatus.PENDING);
        }

        bill = billRepository.save(bill);
        log.info("Venta registrada exitosamente: {}, Total: S/ {}, Vuelto: S/ {}", bill.getBillNumber(), bill.getTotalAmount(), bill.getChangeAmount());
        return mapToResponse(bill);
    }

    @Transactional(readOnly = true)
    public BillResponse getBillById(Long id) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado con ID: " + id));
        return mapToResponse(b);
    }

    @Transactional(readOnly = true)
    public BillResponse getBillByBillNumber(String billNumber) {
        Bill b = billRepository.findByBillNumber(billNumber.trim())
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado con número: " + billNumber));
        return mapToResponse(b);
    }

    @Transactional(readOnly = true)
    public List<BillResponse> getAllBills() {
        return billRepository.findAllOrderByBillDateDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelBill(Long id, String reason) {
        Bill b = billRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado con ID: " + id));
        b.setCancelled(true);
        b.setCancellationReason(reason);
        billRepository.save(b);
        log.info("Comprobante {} cancelado. Motivo: {}", b.getBillNumber(), reason);
    }

    private String generarNumeroFactura() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int rand = new Random().nextInt(9000) + 1000;
        return "FAC-" + timestamp + "-" + rand;
    }

    public BillResponse mapToResponse(Bill b) {
        List<BillItemResponse> itemsDTO = b.getBillItems().stream()
                .map(i -> BillItemResponse.builder()
                        .id(i.getId())
                        .medicineId(i.getMedicineId())
                        .medicineName(i.getMedicineName())
                        .batchNumber(i.getBatchNumber())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .gstPercentage(i.getGstPercentage())
                        .gstAmount(i.getGstAmount())
                        .totalAmount(i.getTotalAmount())
                        .build())
                .collect(Collectors.toList());

        List<PaymentResponse> paymentsDTO = b.getPayments().stream()
                .map(p -> PaymentResponse.builder()
                        .id(p.getId())
                        .paymentReference(p.getPaymentReference())
                        .mode(p.getMode())
                        .amount(p.getAmount())
                        .status(p.getStatus())
                        .paymentDate(p.getPaymentDate())
                        .build())
                .collect(Collectors.toList());

        return BillResponse.builder()
                .id(b.getId())
                .billNumber(b.getBillNumber())
                .billDate(b.getBillDate())
                .cashierId(b.getCashierId())
                .cashierName(b.getCashierName())
                .customerName(b.getCustomerName())
                .customerPhone(b.getCustomerPhone())
                .subtotal(b.getSubtotal())
                .totalGst(b.getTotalGst())
                .totalAmount(b.getTotalAmount())
                .cashTendered(b.getCashTendered())
                .changeAmount(b.getChangeAmount())
                .paymentStatus(b.getPaymentStatus())
                .cancelled(b.getCancelled())
                .cancellationReason(b.getCancellationReason())
                .items(itemsDTO)
                .payments(paymentsDTO)
                .createdAt(b.getCreatedAt())
                .build();
    }
}

