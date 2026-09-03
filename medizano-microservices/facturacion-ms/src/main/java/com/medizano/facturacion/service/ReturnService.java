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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnService {

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final BillRepository billRepository;
    private final BillItemRepository billItemRepository;

    @Transactional
    public ReturnResponse processReturn(ReturnRequest request) {
        Bill bill = billRepository.findById(request.getBillId())
                .orElseThrow(() -> new RuntimeException("Comprobante no encontrado con ID: " + request.getBillId()));

        String returnNumber = "DEV-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + (new Random().nextInt(9000) + 1000);

        BigDecimal totalRefund = BigDecimal.ZERO;
        List<ReturnItem> returnItems = new ArrayList<>();

        Return ret = Return.builder()
                .returnNumber(returnNumber)
                .originalBill(bill)
                .processedById(1L)
                .processedByName("Cajero Principal")
                .returnDate(LocalDateTime.now())
                .refundAmount(BigDecimal.ZERO)
                .reason(request.getReason())
                .returnType(Return.ReturnType.PARTIAL)
                .build();

        ret = returnRepository.save(ret);

        for (ReturnItemRequest itemReq : request.getItems()) {
            BillItem bItem = billItemRepository.findById(itemReq.getBillItemId())
                    .orElseThrow(() -> new RuntimeException("Item de venta no encontrado con ID: " + itemReq.getBillItemId()));

            BigDecimal refundLine = bItem.getUnitPrice().multiply(new BigDecimal(itemReq.getQuantity()));
            totalRefund = totalRefund.add(refundLine);

            ReturnItem rItem = ReturnItem.builder()
                    .returnEntity(ret)
                    .medicineId(bItem.getMedicineId())
                    .medicineName(bItem.getMedicineName())
                    .batchId(bItem.getBatchId())
                    .batchNumber(bItem.getBatchNumber())
                    .quantity(itemReq.getQuantity())
                    .refundAmount(refundLine)
                    .build();

            returnItemRepository.save(rItem);
            returnItems.add(rItem);
        }

        ret.setRefundAmount(totalRefund);
        if (totalRefund.compareTo(bill.getTotalAmount()) >= 0) {
            ret.setReturnType(Return.ReturnType.FULL);
            bill.setPaymentStatus(Bill.PaymentStatus.REFUNDED);
            billRepository.save(bill);
        }
        ret = returnRepository.save(ret);

        return mapToResponse(ret, returnItems);
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

