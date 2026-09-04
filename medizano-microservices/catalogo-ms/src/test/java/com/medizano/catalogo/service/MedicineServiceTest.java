package com.medizano.catalogo.service;

import com.medizano.catalogo.dto.CreateMedicineRequest;
import com.medizano.catalogo.dto.MedicineResponse;
import com.medizano.catalogo.entity.Medicine;
import com.medizano.catalogo.repository.MedicineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicineServiceTest {

    @Mock
    private MedicineRepository medicineRepository;

    @InjectMocks
    private MedicineService medicineService;

    private Medicine sampleMedicine;

    @BeforeEach
    void setUp() {
        sampleMedicine = Medicine.builder()
                .id(1L)
                .name("Paracetamol 500 mg")
                .manufacturer("Portugal")
                .category("Analgésico")
                .barcode("7751234567890")
                .hsnCode("MED-PAR-500-001")
                .gstPercentage(new BigDecimal("18.00"))
                .prescriptionRequired(false)
                .status(Medicine.Status.ACTIVE)
                .purchasePrice(new BigDecimal("2.00"))
                .sellingPrice(new BigDecimal("5.00"))
                .build();
    }

    @Test
    @DisplayName("1. Obtener medicamento por ID devuelve datos y precio de venta oficial")
    void testGetMedicineById() {
        when(medicineRepository.findById(1L)).thenReturn(Optional.of(sampleMedicine));

        MedicineResponse response = medicineService.getMedicineById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Paracetamol 500 mg", response.getName());
        assertEquals(new BigDecimal("5.00"), response.getSellingPrice());
        assertEquals(new BigDecimal("2.00"), response.getPurchasePrice());
        assertEquals("7751234567890", response.getBarcode());
    }

    @Test
    @DisplayName("2. Error al crear medicamento con código HSN duplicado")
    void testCreateMedicineDuplicatedHsn() {
        CreateMedicineRequest request = new CreateMedicineRequest();
        request.setName("Paracetamol Duplicado");
        request.setManufacturer("Genfar");
        request.setHsnCode("MED-PAR-500-001");
        request.setGstPercentage(new BigDecimal("18.00"));
        request.setPrescriptionRequired(false);

        when(medicineRepository.existsByHsnCode("MED-PAR-500-001")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> medicineService.createMedicine(request));
        assertTrue(ex.getMessage().contains("Ya existe un medicamento con el código HSN"));
        verify(medicineRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Buscar por código de barras")
    void testGetMedicineByBarcode() {
        when(medicineRepository.findByBarcode("7751234567890")).thenReturn(Optional.of(sampleMedicine));

        MedicineResponse response = medicineService.findMedicineByBarcode("7751234567890");

        assertNotNull(response);
        assertEquals("Paracetamol 500 mg", response.getName());
        assertEquals(new BigDecimal("5.00"), response.getSellingPrice());
    }

    @Test
    @DisplayName("4. Buscar medicamentos por nombre")
    void testSearchMedicines() {
        when(medicineRepository.findByNameContainingIgnoreCase("Paracetamol")).thenReturn(List.of(sampleMedicine));

        List<MedicineResponse> results = medicineService.searchMedicines("Paracetamol");

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Paracetamol 500 mg", results.get(0).getName());
    }
}
