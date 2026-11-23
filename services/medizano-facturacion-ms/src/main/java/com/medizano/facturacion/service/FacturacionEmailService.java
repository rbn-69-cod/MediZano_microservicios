package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptRequest;
import com.medizano.facturacion.dto.EmailReceiptResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class FacturacionEmailService {

    private final JavaMailSender mailSender;

    public FacturacionEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Value("${spring.mail.username:}")
    private String mailFrom;

    public EmailReceiptResponse enviarComprobante(EmailReceiptRequest request) {
        String recipient = request.getTo() != null ? request.getTo().trim() : "";
        String seriesNumber = request.getSeriesNumber() != null ? request.getSeriesNumber().trim() : "B001-00000001";

        if (recipient.isEmpty()) {
            log.warn("Solicitud de envio cancelada: buzon vacio.");
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("No se proporciono un correo electronico valido")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }

        log.info("Despachando comprobante [{}] hacia [{}] mediante SpaceMail...", seriesNumber, recipient);

        String senderEmail = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "facturacion@univer.lat";
        String htmlContent = FacturacionEmailTemplateBuilder.buildHtmlReceipt(request);
        String plainText = FacturacionEmailTemplateBuilder.buildPlainTextReceipt(request);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(senderEmail, "MediZano Botica");
            helper.setTo(recipient);
            helper.setReplyTo(senderEmail);
            helper.setSubject("Comprobante de Venta " + seriesNumber + " - MediZano Botica");
            helper.setText(plainText, htmlContent);
            mimeMessage.setSentDate(new java.util.Date());

            mailSender.send(mimeMessage);
            log.info("Comprobante [{}] entregado exitosamente a [{}] mediante SpaceMail", seriesNumber, recipient);

            return EmailReceiptResponse.builder()
                    .success(true)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Comprobante enviado exitosamente a: " + recipient)
                    .timestamp(LocalDateTime.now().toString())
                    .build();

        } catch (Exception ex) {
            log.error("Fallo envío SMTP para comprobante [{}] hacia [{}]", seriesNumber, recipient, ex);
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("No se pudo enviar el comprobante. Verifica la configuración del correo e intenta nuevamente.")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }
    }

}
