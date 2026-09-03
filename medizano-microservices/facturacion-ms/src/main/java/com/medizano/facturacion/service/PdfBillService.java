package com.medizano.facturacion.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
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
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    public byte[] generateBillPdf(BillResponse bill) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);
        
        try {
            PdfFont boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont normalFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont smallFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            
            Paragraph header = new Paragraph("MEDIZANO BOTICA")
                    .setFont(boldFont)
                    .setFontSize(20)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5);
            document.add(header);
            
            Paragraph subHeader = new Paragraph("COMPROBANTE DE VENTA")
                    .setFont(boldFont)
                    .setFontSize(14)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(10);
            document.add(subHeader);
            
            Table billInfoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);
            
            billInfoTable.addCell(createCell("Comprobante:", boldFont, 10, false));
            billInfoTable.addCell(createCell(bill.getBillNumber(), normalFont, 10, false));
            
            billInfoTable.addCell(createCell("Fecha:", boldFont, 10, false));
            String dateTime = bill.getBillDate().format(DATE_FORMATTER) + " " + 
                             bill.getBillDate().format(TIME_FORMATTER);
            billInfoTable.addCell(createCell(dateTime, normalFont, 10, false));
            
            billInfoTable.addCell(createCell("Cajero:", boldFont, 10, false));
            billInfoTable.addCell(createCell(bill.getCashierName() != null ? bill.getCashierName() : "Cajero", normalFont, 10, false));
            
            if (bill.getCustomerName() != null && !bill.getCustomerName().trim().isEmpty()) {
                billInfoTable.addCell(createCell("Cliente:", boldFont, 10, false));
                billInfoTable.addCell(createCell(bill.getCustomerName(), normalFont, 10, false));
            }
            
            document.add(billInfoTable);
            
            // Items Table
            Table itemsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1.5f, 1, 1.5f, 1.5f, 1.5f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);
            
            itemsTable.addHeaderCell(createHeaderCell("Producto", boldFont));
            itemsTable.addHeaderCell(createHeaderCell("Lote", boldFont));
            itemsTable.addHeaderCell(createHeaderCell("Cant.", boldFont));
            itemsTable.addHeaderCell(createHeaderCell("P. Unit", boldFont));
            itemsTable.addHeaderCell(createHeaderCell("IGV", boldFont));
            itemsTable.addHeaderCell(createHeaderCell("Total", boldFont));
            
            if (bill.getItems() != null) {
                for (BillItemResponse item : bill.getItems()) {
                    itemsTable.addCell(createCell(item.getMedicineName() != null ? item.getMedicineName() : "Medicamento #" + item.getMedicineId(), normalFont, 9, true));
                    itemsTable.addCell(createCell(item.getBatchNumber() != null ? item.getBatchNumber() : "-", normalFont, 9, true));
                    itemsTable.addCell(createCell(String.valueOf(item.getQuantity()), normalFont, 9, true));
                    itemsTable.addCell(createCell("S/ " + formatAmount(item.getUnitPrice()), normalFont, 9, true));
                    itemsTable.addCell(createCell("S/ " + formatAmount(item.getGstAmount()), normalFont, 9, true));
                    itemsTable.addCell(createCell("S/ " + formatAmount(item.getTotalAmount()), normalFont, 9, true));
                }
            }
            
            document.add(itemsTable);
            
            // Totals Table
            Table totalsTable = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                    .useAllAvailableWidth()
                    .setMarginBottom(15);
            
            totalsTable.addCell(createCell("Subtotal:", boldFont, 10, false, TextAlignment.RIGHT));
            totalsTable.addCell(createCell("S/ " + formatAmount(bill.getSubtotal()), normalFont, 10, false));
            
            totalsTable.addCell(createCell("Total IGV:", boldFont, 10, false, TextAlignment.RIGHT));
            totalsTable.addCell(createCell("S/ " + formatAmount(bill.getTotalGst()), normalFont, 10, false));
            
            totalsTable.addCell(createCell("TOTAL:", boldFont, 12, false, TextAlignment.RIGHT));
            totalsTable.addCell(createCell("S/ " + formatAmount(bill.getTotalAmount()), boldFont, 12, false));
            
            document.add(totalsTable);
            
            // Footer
            Paragraph footer = new Paragraph("¡Gracias por su compra en MediZano!")
                    .setFont(normalFont)
                    .setFontSize(10)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20);
            document.add(footer);
            
        } finally {
            document.close();
        }
        
        return baos.toByteArray();
    }
    
    private Cell createCell(String text, PdfFont font, float fontSize, boolean border) {
        return createCell(text, font, fontSize, border, TextAlignment.LEFT);
    }
    
    private Cell createCell(String text, PdfFont font, float fontSize, boolean border, TextAlignment alignment) {
        Cell cell = new Cell().add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(fontSize));
        cell.setTextAlignment(alignment);
        if (!border) {
            cell.setBorder(com.itextpdf.layout.borders.Border.NO_BORDER);
        }
        return cell;
    }
    
    private Cell createHeaderCell(String text, PdfFont font) {
        Cell cell = new Cell().add(new Paragraph(text).setFont(font).setFontSize(10));
        cell.setBackgroundColor(ColorConstants.LIGHT_GRAY);
        cell.setTextAlignment(TextAlignment.CENTER);
        return cell;
    }
    
    private String formatAmount(BigDecimal amount) {
        return amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() : "0.00";
    }
}

