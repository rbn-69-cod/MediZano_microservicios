package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.ReturnItemRequest;
import com.medizano.facturacion.dto.ReturnItemResponse;
import com.medizano.facturacion.dto.ReturnRequest;
import com.medizano.facturacion.dto.ReturnResponse;
import com.medizano.facturacion.entity.Bill;
import com.medizano.facturacion.entity.BillItem;
import com.medizano.facturacion.entity.Return;
import com.medizano.facturacion.entity.ReturnItem;
import com.medizano.facturacion.repository.BillItemRepository;
import com.medizano.facturacion.repository.BillRepository;
import com.medizano.facturacion.repository.ReturnItemRepository;
import com.medizano.facturacion.repository.ReturnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnService {

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;
    private final com.medizano.facturacion.client.InventarioClient inventarioClient;

    @Transactional
    public ReturnResponse processReturn(ReturnRequest request, Long processedById, String processedByName) {
        Bill bill = billRepository.findByIdForUpdate(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado con ID: " + request.getBillId()));

        if (Boolean.TRUE.equals(bill.getCancelled())) {
            throw new IllegalStateException("No se puede devolver un comprobante anulado");
        }
        if (bill.getPaymentStatus() != Bill.PaymentStatus.PAID) {
            throw new IllegalStateException("Solo se pueden devolver comprobantes pagados");
        }
        if (returnItemRepository.countLegacyItemsByBillId(bill.getId()) > 0) {
            throw new IllegalStateException("La venta contiene devoluciones antiguas sin trazabilidad de línea; requiere revisión manual");
        }

        String returnNumber = "DEV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + (new Random().nextInt(9000) + 1000);

        BigDecimal totalRefund = BigDecimal.ZERO;
        List<ReturnItem> returnItems = new ArrayList<>();

        Return ret = Return.builder()
                .returnNumber(returnNumber)
                .originalBill(bill)
                .processedById(processedById)
                .processedByName(processedByName != null ? processedByName : "Usuario autenticado")
                .returnDate(LocalDateTime.now())
                .refundAmount(BigDecimal.ZERO)
                .reason(request.getReason())
                .returnType(Return.ReturnType.PARTIAL)
                .build();

        ret = returnRepository.save(ret);

        Set<Long> requestedItemIds = new HashSet<>();
        for (ReturnItemRequest itemReq : request.getItems()) {
            if (!requestedItemIds.add(itemReq.getBillItemId())) {
                throw new IllegalArgumentException("No se puede repetir el mismo ítem dentro de una devolución");
            }
            BillItem bItem = billItemRepository.findById(itemReq.getBillItemId())
                    .orElseThrow(() -> new RuntimeException("Item de venta no encontrado con ID: " + itemReq.getBillItemId()));

            if (bItem.getBill() == null || !bill.getId().equals(bItem.getBill().getId())) {
                throw new IllegalArgumentException("El ítem " + itemReq.getBillItemId() + " no pertenece al comprobante indicado");
            }
            long alreadyReturned = returnItemRepository.sumQuantityByBillItemId(bItem.getId());
            long availableToReturn = bItem.getQuantity() - alreadyReturned;
            if (itemReq.getQuantity() > availableToReturn) {
                throw new IllegalArgumentException("La cantidad solicitada supera lo disponible para devolver. Máximo: " + availableToReturn);
            }

            BigDecimal refundLine = bItem.getUnitPrice().multiply(new BigDecimal(itemReq.getQuantity()));
            totalRefund = totalRefund.add(refundLine);

            ReturnItem rItem = ReturnItem.builder()
                    .returnEntity(ret)
                    .medicineId(bItem.getMedicineId())
                    .billItemId(bItem.getId())
                    .medicineName(bItem.getMedicineName())
                    .batchId(bItem.getBatchId())
                    .batchNumber(bItem.getBatchNumber())
                    .quantity(itemReq.getQuantity())
                    .refundAmount(refundLine)
                    .build();

            returnItemRepository.save(rItem);
            returnItems.add(rItem);
        }

        Long currentReturnId = ret.getId();
        BigDecimal priorRefunds = returnRepository.findByOriginalBillId(bill.getId()).stream()
                .filter(existing -> !existing.getId().equals(currentReturnId))
                .map(Return::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cumulativeRefund = priorRefunds.add(totalRefund);
        if (cumulativeRefund.compareTo(bill.getTotalAmount()) > 0) {
            throw new IllegalStateException("El reembolso acumulado no puede superar el total del comprobante");
        }

        ret.setRefundAmount(totalRefund);
        if (cumulativeRefund.compareTo(bill.getTotalAmount()) == 0) {
            ret.setReturnType(Return.ReturnType.FULL);
            bill.setPaymentStatus(Bill.PaymentStatus.REFUNDED);
            billRepository.save(bill);
        }
        ret = returnRepository.save(ret);

        restaurarInventario(ret, returnItems);

        return mapToResponse(ret, returnItems);
    }

    private void restaurarInventario(Return ret, List<ReturnItem> items) {
        try {
            List<com.medizano.facturacion.client.InventarioClient.ItemRestock> stockItems = items.stream()
                    .map(item -> new com.medizano.facturacion.client.InventarioClient.ItemRestock(
                            item.getMedicineId(), item.getBatchId(), item.getQuantity()))
                    .collect(Collectors.toList());
            inventarioClient.reponerStockDevolucion(
                    new com.medizano.facturacion.client.InventarioClient.RestockStockRequest(ret.getReturnNumber(), stockItems));
            ret.setInventoryRestored(true);
            ret.setInventoryError(null);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            ret.setInventoryRestored(false);
            ret.setInventoryError(message.substring(0, Math.min(message.length(), 500)));
            log.error("Devolución {} registrada, pendiente de reponer inventario: {}", ret.getReturnNumber(), message);
        }
        returnRepository.save(ret);
    }

    @Scheduled(fixedDelayString = "${returns.inventory-retry-delay-ms:30000}")
    @Transactional
    public void retryPendingInventoryRestoration() {
        returnRepository.findPendingInventoryRestoration().stream().limit(50).forEach(ret ->
                restaurarInventario(ret, returnItemRepository.findByReturnEntityId(ret.getId())));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getAllReturns() {
        return returnRepository.findAll().stream()
                .map(r -> mapToResponse(r, returnItemRepository.findByReturnEntityId(r.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReturnResponse getReturnById(Long id) {
        Return r = returnRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Devolución no encontrada con ID: " + id));
        return mapToResponse(r, returnItemRepository.findByReturnEntityId(r.getId()));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getReturnsByBillId(Long billId) {
        return returnRepository.findByOriginalBillId(billId).stream()
                .map(r -> mapToResponse(r, returnItemRepository.findByReturnEntityId(r.getId())))
                .collect(Collectors.toList());
    }

    private ReturnResponse mapToResponse(Return r, List<ReturnItem> items) {
        List<ReturnItemResponse> itemDtos = items.stream()
                .map(i -> ReturnItemResponse.builder()
                        .id(i.getId())
                        .medicineId(i.getMedicineId())
                        .medicineName(i.getMedicineName())
                        .batchId(i.getBatchId())
                        .batchNumber(i.getBatchNumber())
                        .quantity(i.getQuantity())
                        .refundAmount(i.getRefundAmount())
                        .build())
                .collect(Collectors.toList());

        return ReturnResponse.builder()
                .id(r.getId())
                .returnNumber(r.getReturnNumber())
                .billId(r.getOriginalBill().getId())
                .billNumber(r.getOriginalBill().getBillNumber())
                .processedById(r.getProcessedById())
                .processedByName(r.getProcessedByName())
                .returnDate(r.getReturnDate())
                .refundAmount(r.getRefundAmount())
                .reason(r.getReason())
                .returnType(r.getReturnType())
                .createdAt(r.getCreatedAt())
                .items(itemDtos)
                .build();
    }
}
