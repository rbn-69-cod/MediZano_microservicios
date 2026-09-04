package com.medizano.facturacion.service;

import com.medizano.facturacion.client.CatalogoClient;
import com.medizano.facturacion.dto.BillItemRequest;
import com.medizano.facturacion.dto.BillResponse;
import com.medizano.facturacion.dto.CreateBillRequest;
import com.medizano.facturacion.dto.PaymentRequest;
import com.medizano.facturacion.entity.Bill;
import com.medizano.facturacion.entity.Payment;
import com.medizano.facturacion.repository.BillItemRepository;
import com.medizano.facturacion.repository.BillRepository;
import com.medizano.facturacion.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock
    private BillRepository billRepository;

    @Mock
    private BillItemRepository billItemRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CatalogoClient catalogoClient;

    @InjectMocks
    private BillingService billingService;

    private CatalogoClient.MedicineClientResponse paracetamol;

    @BeforeEach
    void setUp() {
        paracetamol = new CatalogoClient.MedicineClientResponse();
        paracetamol.setId(1L);
        paracetamol.setName("Paracetamol 500 mg");
        paracetamol.setSellingPrice(new BigDecimal("5.00"));
        paracetamol.setGstPercentage(new BigDecimal("18.00"));
        paracetamol.setBarcode("7751234567890");
    }

    @Test
    @DisplayName("1. Crear factura con precio oficial calcula subtotal, IGV y total exactos")
    void testCreateBillConPrecioOficial() {
        when(catalogoClient.getMedicineById(1L)).thenReturn(paracetamol);
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill b = invocation.getArgument(0);
            b.setId(10L);
            return b;
        });

        BillItemRequest item = new BillItemRequest();
        item.setMedicineId(1L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("5.00"));

        PaymentRequest payment = new PaymentRequest();
        payment.setMode(Payment.PaymentMode.CASH);
        payment.setAmount(new BigDecimal("10.00"));

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerName("Juan Pérez");
        request.setItems(List.of(item));
        request.setPayments(List.of(payment));

        BillResponse response = billingService.createBill(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("10.00"), response.getTotalAmount());
        assertEquals(Bill.PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals("Juan Pérez", response.getCustomerName());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("5.00"), response.getItems().get(0).getUnitPrice());
    }

    @Test
    @DisplayName("2. Anti-manipulación: lanza excepción si el precio enviado difiere del precio oficial de catálogo")
    void testAntiManipulacionPrecio() {
        when(catalogoClient.getMedicineById(1L)).thenReturn(paracetamol);

        BillItemRequest item = new BillItemRequest();
        item.setMedicineId(1L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("0.50")); // Intento de manipulación de precio

        CreateBillRequest request = new CreateBillRequest();
        request.setCustomerName("Hacker");
        request.setItems(List.of(item));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> billingService.createBill(request));

        assertTrue(ex.getMessage().contains("Discrepancia de precio detectada"));
        verify(billRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Obtener factura por ID existente")
    void testGetBillById() {
        Bill bill = Bill.builder()
                .id(1L)
                .billNumber("BILL-2026-001")
                .billDate(LocalDateTime.now())
                .customerName("María García")
                .subtotal(new BigDecimal("8.47"))
                .totalGst(new BigDecimal("1.53"))
                .totalAmount(new BigDecimal("10.00"))
                .paymentStatus(Bill.PaymentStatus.PAID)
                .billItems(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();

        when(billRepository.findById(1L)).thenReturn(Optional.of(bill));

        BillResponse response = billingService.getBillById(1L);

        assertNotNull(response);
        assertEquals("BILL-2026-001", response.getBillNumber());
        assertEquals(new BigDecimal("10.00"), response.getTotalAmount());
    }
}
