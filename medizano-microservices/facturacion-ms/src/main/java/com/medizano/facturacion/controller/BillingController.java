package com.medizano.facturacion.controller;

import com.medizano.facturacion.dto.BillResponse;
import com.medizano.facturacion.dto.CreateBillRequest;
import com.medizano.facturacion.service.BillingService;
import com.medizano.facturacion.service.PdfBillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cashier/bills")
@RequiredArgsConstructor
@Tag(name = "Facturación POS", description = "Emisión de comprobantes y ventas POS")
public class BillingController {

    private final BillingService billingService;
    private final PdfBillService pdfBillService;

    @PostMapping
    @Operation(summary = "Crear comprobante de venta POS")
    public ResponseEntity<BillResponse> createBill(@Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.createBill(request));
    }

    @GetMapping
    @Operation(summary = "Listar todas las ventas")
    public ResponseEntity<List<BillResponse>> getAllBills() {
        return ResponseEntity.ok(billingService.getAllBills());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener comprobante por ID")
    public ResponseEntity<BillResponse> getBillById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getBillById(id));
    }

    @GetMapping("/number/{billNumber}")
    @Operation(summary = "Obtener comprobante por número")
    public ResponseEntity<BillResponse> getBillByNumber(@PathVariable String billNumber) {
        return ResponseEntity.ok(billingService.getBillByBillNumber(billNumber));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Descargar comprobante en PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        BillResponse bill = billingService.getBillById(id);
        byte[] pdfBytes;
        try {
            pdfBytes = pdfBillService.generateBillPdf(bill);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Error generando PDF para comprobante ID: " + id, e);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "comprobante-" + bill.getBillNumber() + ".pdf");
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Anular comprobante")
    public ResponseEntity<Void> cancelBill(@PathVariable Long id, @RequestParam(defaultValue = "Anulación administrativa") String reason) {
        billingService.cancelBill(id, reason);
        return ResponseEntity.noContent().build();
    }
}
