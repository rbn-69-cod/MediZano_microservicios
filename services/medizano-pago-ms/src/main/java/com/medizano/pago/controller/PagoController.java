package com.medizano.pago.controller;

import com.medizano.pago.dto.*;
import com.medizano.pago.service.CulqiPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {
    private final CulqiPaymentService culqi;
    @GetMapping("/config") public ResponseEntity<CulqiConfigResponse> config() { return ResponseEntity.ok(culqi.publicConfig()); }
    @GetMapping("/orden/{ordenId}") public ResponseEntity<List<PagoDTO>> byOrder(@PathVariable Long ordenId) { return ResponseEntity.ok(culqi.listByOrder(ordenId)); }
    @GetMapping("/{id}") public ResponseEntity<PagoDTO> byId(@PathVariable Long id) { return ResponseEntity.ok(culqi.get(id)); }
    @PostMapping("/culqi/qr/order") public ResponseEntity<CulqiOrderResponse> qr(@Valid @RequestBody CulqiOrderRequest request) { return ResponseEntity.ok(culqi.createWalletOrder(request)); }
    @PostMapping("/culqi/yape/charge") public ResponseEntity<CulqiPaymentResponse> yape(@Valid @RequestBody CulqiChargeRequest request) { return ResponseEntity.ok(culqi.chargeYape(request)); }
    @PostMapping("/culqi/webhook") public ResponseEntity<Void> webhook(@RequestBody Map<String,Object> event) { culqi.processWebhook(event); return ResponseEntity.ok().build(); }
}
