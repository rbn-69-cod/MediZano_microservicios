package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptItemDTO;
import com.medizano.facturacion.dto.EmailReceiptRequest;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class FacturacionEmailTemplateBuilder {
    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");
    private static final DateTimeFormatter PERU_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.forLanguageTag("es-PE"));

    public static String buildHtmlReceipt(EmailReceiptRequest req) {
        String fecha = value(req.getDate(), ZonedDateTime.now(PERU_ZONE).format(PERU_FORMATTER));
        String series = value(req.getSeriesNumber(), "B001-00000001");
        String customerName = value(req.getCustomerName(), "Cliente General");
        String customerDoc = value(req.getCustomerDoc(), "Sin documento");
        String paymentMode = value(req.getPaymentMode(), "EFECTIVO");
        BigDecimal subtotal = amount(req.getSubtotal()), igv = amount(req.getIgv()), total = amount(req.getTotal());
        StringBuilder items = new StringBuilder();
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            for (EmailReceiptItemDTO it : req.getItems()) {
                items.append("<tr><td style=\"padding:16px 12px;border-bottom:1px solid #e8eef7;font-size:14px;color:#13213c;\">")
                        .append(escapeHtml(value(it.getName(), "Medicamento"))).append("</td><td style=\"padding:16px 8px;border-bottom:1px solid #e8eef7;text-align:center;font-size:14px;color:#50617d;\">")
                        .append(it.getQuantity() != null ? it.getQuantity() : 1).append("</td><td style=\"padding:16px 8px;border-bottom:1px solid #e8eef7;text-align:right;font-size:14px;color:#50617d;white-space:nowrap;\">S/ ")
                        .append(money(it.getUnitPrice())).append("</td><td style=\"padding:16px 12px;border-bottom:1px solid #e8eef7;text-align:right;font-size:14px;color:#13213c;font-weight:700;white-space:nowrap;\">S/ ")
                        .append(money(it.getTotal())).append("</td></tr>");
            }
        } else items.append("<tr><td colspan=\"4\" style=\"padding:18px;text-align:center;color:#7b8aa5;\">Venta de mostrador</td></tr>");

        return "<!doctype html><html lang=\"es\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>Comprobante " + escapeHtml(series) + "</title></head>"
                + "<body style=\"margin:0;padding:28px 10px;background:#eef4fb;font-family:Arial,Helvetica,sans-serif;color:#13213c;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"620\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:100%;max-width:620px;background:#fff;border:1px solid #dce6f3;border-radius:18px;overflow:hidden;\">"
                + "<tr><td style=\"background:#102f86;padding:28px 30px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td><table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr><td style=\"width:44px;height:44px;background:#27c4bd;border-radius:14px;text-align:center;vertical-align:middle;color:#fff;font-size:25px;font-weight:bold;\">+</td><td style=\"padding-left:13px;\"><div style=\"color:#fff;font-size:23px;font-weight:800;\">MediZano</div><div style=\"color:#b9d4ff;font-size:12px;margin-top:3px;\">Botica &amp; Farmacia Especializada</div></td></tr></table></td><td align=\"right\" valign=\"top\" style=\"color:#d7e5ff;font-size:12px;\">COMPROBANTE<br><strong style=\"color:#fff;font-size:14px;\">DIGITAL</strong></td></tr></table></td></tr>"
                + "<tr><td style=\"padding:24px 30px 10px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td><div style=\"font-size:21px;font-weight:800;color:#13213c;\">Venta registrada</div><div style=\"font-size:13px;color:#71819b;margin-top:5px;\">Gracias por comprar con nosotros</div></td><td align=\"right\"><span style=\"display:inline-block;padding:8px 12px;background:#e8fbf3;color:#087b58;border-radius:30px;font-size:12px;font-weight:700;\">✓ PAGO CONFIRMADO</span></td></tr></table></td></tr>"
                + "<tr><td style=\"padding:12px 30px 20px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#f6f9fd;border:1px solid #e3ebf5;border-radius:12px;\"><tr>" + infoCell("Comprobante", series) + infoCell("Fecha", fecha) + "</tr><tr>" + infoCell("Cliente", customerName) + infoCell("Pago", paymentMode) + "</tr></table></td></tr>"
                + "<tr><td style=\"padding:0 30px 10px;\"><div style=\"font-size:12px;font-weight:800;letter-spacing:.7px;color:#6c7d99;text-transform:uppercase;margin:8px 0 12px;\">Detalle de compra</div><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border:1px solid #e3ebf5;border-radius:12px;overflow:hidden;border-collapse:separate;\"><tr style=\"background:#f1f5fa;\"><th align=\"left\" style=\"padding:12px;font-size:11px;color:#63738e;text-transform:uppercase;\">Producto</th><th style=\"padding:12px 8px;font-size:11px;color:#63738e;text-transform:uppercase;\">Cant.</th><th align=\"right\" style=\"padding:12px 8px;font-size:11px;color:#63738e;text-transform:uppercase;\">P. Unit.</th><th align=\"right\" style=\"padding:12px;font-size:11px;color:#63738e;text-transform:uppercase;\">Total</th></tr>" + items + "</table></td></tr>"
                + "<tr><td style=\"padding:18px 30px 28px;\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td width=\"48%\" valign=\"top\" style=\"font-size:12px;color:#7b8aa5;line-height:1.6;\">Documento: <strong style=\"color:#50617d;\">" + escapeHtml(customerDoc) + "</strong><br>Conserve este comprobante para sus registros.</td><td width=\"52%\"><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\">" + totalRow("Subtotal", subtotal) + totalRow("IGV (18%)", igv) + "<tr><td colspan=\"2\" style=\"padding-top:12px;border-top:2px solid #dbe5f1;\"></td></tr><tr><td style=\"font-size:15px;font-weight:800;color:#13213c;\">TOTAL</td><td align=\"right\" style=\"font-size:22px;font-weight:900;color:#07835f;white-space:nowrap;\">S/ " + money(total) + "</td></tr></table></td></tr></table></td></tr>"
                + "<tr><td style=\"padding:18px 30px;background:#f7faff;border-top:1px solid #e3ebf5;text-align:center;\"><div style=\"font-size:13px;color:#50617d;\">Gracias por confiar en <strong style=\"color:#102f86;\">MediZano Botica</strong></div><div style=\"font-size:11px;color:#91a0b6;margin-top:6px;\">Este comprobante fue generado electrónicamente.</div></td></tr></table></td></tr></table></body></html>";
    }

    private static String infoCell(String label, String value) { return "<td width=\"50%\" style=\"padding:12px 14px;\"><div style=\"font-size:11px;color:#8392aa;text-transform:uppercase;letter-spacing:.4px;\">" + label + "</div><div style=\"font-size:13px;color:#172642;font-weight:700;margin-top:4px;\">" + escapeHtml(value) + "</div></td>"; }
    private static String totalRow(String label, BigDecimal value) { return "<tr><td style=\"padding:4px 0;font-size:13px;color:#71819b;\">" + label + "</td><td align=\"right\" style=\"padding:4px 0;font-size:13px;color:#253653;white-space:nowrap;\">S/ " + money(value) + "</td></tr>"; }
    private static BigDecimal amount(BigDecimal value) { return value != null ? value : BigDecimal.ZERO; }
    private static String money(BigDecimal value) { return String.format(Locale.US, "%.2f", amount(value)); }
    private static String value(String value, String fallback) { return value != null && !value.isBlank() ? value : fallback; }

    public static String buildPlainTextReceipt(EmailReceiptRequest req) {
        String fecha = value(req.getDate(), ZonedDateTime.now(PERU_ZONE).format(PERU_FORMATTER));
        StringBuilder sb = new StringBuilder("MEDIZANO BOTICA - COMPROBANTE DE VENTA\n=========================================\n");
        sb.append("Comprobante: ").append(value(req.getSeriesNumber(), "B001-00000001")).append("\nFecha (Perú): ").append(fecha).append("\nCliente: ").append(value(req.getCustomerName(), "Cliente General")).append("\nDocumento: ").append(value(req.getCustomerDoc(), "Sin documento")).append("\nPago: ").append(value(req.getPaymentMode(), "EFECTIVO")).append("\n-----------------------------------------\nPRODUCTOS:\n");
        if (req.getItems() != null) for (EmailReceiptItemDTO it : req.getItems()) sb.append(String.format(Locale.US, "  - %dx %s: S/ %.2f\n", it.getQuantity() != null ? it.getQuantity() : 1, value(it.getName(), "Item"), amount(it.getTotal())));
        sb.append("-----------------------------------------\n").append(String.format(Locale.US, "Subtotal: S/ %.2f\nIGV (18%%): S/ %.2f\nTOTAL: S/ %.2f\n", amount(req.getSubtotal()), amount(req.getIgv()), amount(req.getTotal()))).append("=========================================\nGracias por su compra en MediZano Botica.\n");
        return sb.toString();
    }

    private static String escapeHtml(String text) { if (text == null) return ""; return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
