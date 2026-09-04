package com.medizano.facturacion.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.medizano.facturacion.dto.BillItemResponse;
import com.medizano.facturacion.dto.BillResponse;
import com.medizano.facturacion.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class PdfBillService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(2, 132, 199);
    private static final DeviceRgb TEXT_DARK = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb BG_LIGHT = new DeviceRgb(241, 245, 249);

    public byte[] generateBillPdf(BillResponse bill) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        document.setMargins(30, 30, 30, 30);

        try {
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // 1. Header institucional
            Paragraph brandHeader = new Paragraph("MEDIZANO BOTICA")
                    .setFont(boldFont)
                    .setFontSize(22)
                    .setFontColor(PRIMARY_COLOR)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(2);
            document.add(brandHeader);

            Paragraph docType = new Paragraph("Comprobante de Venta")
                    .setFont(boldFont)
                    .setFontSize(13)
                    .setFontColor(TEXT_DARK)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(12);
            document.add(docType);

            document.add(new Paragraph("----------------------------------------------------------------------------------------------------")
                    .setFont(normalFont)
                    .setFontSize(8)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10));

            // 2. Información general del comprobante y cliente
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{25, 75}))
                    .useAllAvailableWidth()
                    .setMarginBottom(14);

            addInfoRow(infoTable, "Número:", bill.getBillNumber() != null ? bill.getBillNumber() : "FAC-" + bill.getId(), boldFont, normalFont);
            
            String fechaStr = bill.getBillDate() != null ? bill.getBillDate().format(DATE_TIME_FORMATTER) : "N/A";
            addInfoRow(infoTable, "Fecha:", fechaStr, boldFont, normalFont);

            String clienteNombre = bill.getCustomerName() != null && !bill.getCustomerName().trim().isEmpty()
                    && !"null".equalsIgnoreCase(bill.getCustomerName().trim())
                    ? bill.getCustomerName().trim()
                    : "Cliente general";
            addInfoRow(infoTable, "Cliente:", clienteNombre, boldFont, normalFont);

            // Teléfono (solo si existe y no es vacío/null)
            if (bill.getCustomerPhone() != null && !bill.getCustomerPhone().trim().isEmpty()
                    && !"null".equalsIgnoreCase(bill.getCustomerPhone().trim())) {
                addInfoRow(infoTable, "Celular:", bill.getCustomerPhone().trim(), boldFont, normalFont);
            }

            // Correo (solo si existe y no es vacío/null)
            if (bill.getCustomerEmail() != null && !bill.getCustomerEmail().trim().isEmpty()
                    && !"null".equalsIgnoreCase(bill.getCustomerEmail().trim())) {
                addInfoRow(infoTable, "Correo:", bill.getCustomerEmail().trim(), boldFont, normalFont);
            }

            if (bill.getCashierName() != null && !bill.getCashierName().trim().isEmpty()
                    && !"null".equalsIgnoreCase(bill.getCashierName().trim())) {
                addInfoRow(infoTable, "Atendido por:", bill.getCashierName().trim(), boldFont, normalFont);
            }

            document.add(infoTable);

            // 3. Sección DETALLE
            Paragraph detalleTitle = new Paragraph("DETALLE")
                    .setFont(boldFont)
                    .setFontSize(11)
                    .setFontColor(PRIMARY_COLOR)
                    .setMarginBottom(6);
            document.add(detalleTitle);

            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{45, 15, 20, 20}))
                    .useAllAvailableWidth()
                    .setMarginBottom(14);

            itemsTable.addHeaderCell(createHeaderCell("Medicamento / Descripción", boldFont, TextAlignment.LEFT));
            itemsTable.addHeaderCell(createHeaderCell("Cant.", boldFont, TextAlignment.CENTER));
            itemsTable.addHeaderCell(createHeaderCell("Precio unit.", boldFont, TextAlignment.RIGHT));
            itemsTable.addHeaderCell(createHeaderCell("Subtotal", boldFont, TextAlignment.RIGHT));

            if (bill.getItems() != null && !bill.getItems().isEmpty()) {
                for (BillItemResponse item : bill.getItems()) {
                    String medName = item.getMedicineName() != null && !item.getMedicineName().trim().isEmpty()
                            ? item.getMedicineName().trim()
                            : "Medicamento #" + item.getMedicineId();

                    int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                    BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal lineTotal = item.getTotalAmount() != null ? item.getTotalAmount() : unitPrice.multiply(BigDecimal.valueOf(qty));

                    // Col 1: Medicamento + desglose "2 x S/ 5.00"
                    Paragraph medDesc = new Paragraph()
                            .add(new Paragraph(medName).setFont(boldFont).setFontSize(9))
                            .add(new Paragraph("\n" + qty + " x S/ " + formatMoney(unitPrice)).setFont(normalFont).setFontSize(8).setFontColor(ColorConstants.DARK_GRAY));

                    Cell medCell = new Cell().add(medDesc).setPadding(6);
                    itemsTable.addCell(medCell);

                    // Col 2: Cantidad
                    itemsTable.addCell(new Cell().add(new Paragraph(String.valueOf(qty)).setFont(normalFont).setFontSize(9))
                            .setTextAlignment(TextAlignment.CENTER).setPadding(6));

                    // Col 3: Precio unitario
                    itemsTable.addCell(new Cell().add(new Paragraph("S/ " + formatMoney(unitPrice)).setFont(normalFont).setFontSize(9))
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));

                    // Col 4: Subtotal
                    itemsTable.addCell(new Cell().add(new Paragraph("S/ " + formatMoney(lineTotal)).setFont(boldFont).setFontSize(9))
                            .setTextAlignment(TextAlignment.RIGHT).setPadding(6));
                }
            }

            document.add(itemsTable);

            // 4. Sección RESUMEN (Subtotal, IGV, TOTAL)
            Paragraph resumenTitle = new Paragraph("RESUMEN")
                    .setFont(boldFont)
                    .setFontSize(11)
                    .setFontColor(PRIMARY_COLOR)
                    .setMarginBottom(6);
            document.add(resumenTitle);

            Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{65, 35}))
                    .useAllAvailableWidth()
                    .setMarginBottom(14);

            addSummaryRow(totalsTable, "Subtotal:", "S/ " + formatMoney(bill.getSubtotal()), normalFont, normalFont, false);
            addSummaryRow(totalsTable, "IGV (18%):", "S/ " + formatMoney(bill.getTotalGst()), normalFont, normalFont, false);
            addSummaryRow(totalsTable, "TOTAL:", "S/ " + formatMoney(bill.getTotalAmount()), boldFont, boldFont, true);

            document.add(totalsTable);

            // 5. Sección PAGO
            Paragraph pagoTitle = new Paragraph("PAGO")
                    .setFont(boldFont)
                    .setFontSize(11)
                    .setFontColor(PRIMARY_COLOR)
                    .setMarginBottom(6);
            document.add(pagoTitle);

            Table pagoTable = new Table(UnitValue.createPercentArray(new float[]{40, 60}))
                    .useAllAvailableWidth()
                    .setMarginBottom(16);

            String metodoPrincipal = "Efectivo";
            boolean esEfectivo = true;

            if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                PaymentResponse p = bill.getPayments().get(0);
                if (p.getMode() != null) {
                    switch (p.getMode()) {
                        case PAYPAL:
                            metodoPrincipal = "PayPal";
                            esEfectivo = false;
                            break;
                        case MERCADO_PAGO:
                            metodoPrincipal = "Mercado Pago";
                            esEfectivo = false;
                            break;
                        default:
                            metodoPrincipal = "Efectivo";
                            esEfectivo = true;
                            break;
                    }
                }
            }

            addInfoRow(pagoTable, "Método de pago:", metodoPrincipal, boldFont, normalFont);

            if (esEfectivo) {
                BigDecimal totalVenta = bill.getTotalAmount() != null ? bill.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal recibido = bill.getCashTendered() != null && bill.getCashTendered().compareTo(BigDecimal.ZERO) > 0
                        ? bill.getCashTendered()
                        : totalVenta;
                BigDecimal vuelto = bill.getChangeAmount() != null && bill.getChangeAmount().compareTo(BigDecimal.ZERO) >= 0
                        ? bill.getChangeAmount()
                        : (recibido.compareTo(totalVenta) > 0 ? recibido.subtract(totalVenta) : BigDecimal.ZERO);

                addInfoRow(pagoTable, "Monto recibido:", "S/ " + formatMoney(recibido), boldFont, normalFont);
                addInfoRow(pagoTable, "Vuelto:", "S/ " + formatMoney(vuelto), boldFont, normalFont);
            } else {
                // Pago digital (PayPal / Mercado Pago): NO mostrar monto recibido ni vuelto
                if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                    String ref = bill.getPayments().get(0).getPaymentReference();
                    if (ref != null && !ref.trim().isEmpty() && !"null".equalsIgnoreCase(ref.trim())) {
                        addInfoRow(pagoTable, "Referencia:", ref.trim(), boldFont, normalFont);
                    }
                }
            }

            document.add(pagoTable);

            // 6. Footer
            Paragraph footer = new Paragraph("¡Gracias por su compra en MediZano!\nComprobante electrónico emitido con éxito.")
                    .setFont(normalFont)
                    .setFontSize(9)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.DARK_GRAY)
                    .setMarginTop(12);
            document.add(footer);

        } finally {
            document.close();
        }

        return baos.toByteArray();
    }

    private void addInfoRow(Table table, String label, String value, PdfFont labelFont, PdfFont valueFont) {
        Cell labelCell = new Cell().add(new Paragraph(label).setFont(labelFont).setFontSize(9))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(3);
        Cell valueCell = new Cell().add(new Paragraph(value != null ? value : "").setFont(valueFont).setFontSize(9))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(3);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addSummaryRow(Table table, String label, String value, PdfFont labelFont, PdfFont valueFont, boolean isTotal) {
        Paragraph pLabel = new Paragraph(label).setFont(labelFont).setFontSize(isTotal ? 11 : 9);
        Paragraph pVal = new Paragraph(value).setFont(valueFont).setFontSize(isTotal ? 11 : 9);

        if (isTotal) {
            pLabel.setFontColor(PRIMARY_COLOR);
            pVal.setFontColor(PRIMARY_COLOR);
        }

        Cell labelCell = new Cell().add(pLabel)
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(4);

        Cell valCell = new Cell().add(pVal)
                .setTextAlignment(TextAlignment.RIGHT)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(4);

        if (isTotal) {
            labelCell.setBackgroundColor(BG_LIGHT);
            valCell.setBackgroundColor(BG_LIGHT);
        }

        table.addCell(labelCell);
        table.addCell(valCell);
    }

    private Cell createHeaderCell(String text, PdfFont font, TextAlignment alignment) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(9).setFontColor(ColorConstants.WHITE))
                .setBackgroundColor(PRIMARY_COLOR)
                .setTextAlignment(alignment)
                .setPadding(6);
    }

    private String formatMoney(BigDecimal amount) {
        return amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00";
    }
}

