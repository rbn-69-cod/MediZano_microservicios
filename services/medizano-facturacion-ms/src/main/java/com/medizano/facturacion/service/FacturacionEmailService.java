package com.medizano.facturacion.service;

import com.medizano.facturacion.dto.EmailReceiptRequest;
import com.medizano.facturacion.dto.EmailReceiptResponse;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacturacionEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${spring.mail.host:mailpit}")
    private String mailHost;

    @Value("${spring.mail.port:1025}")
    private int mailPort;

    public EmailReceiptResponse enviarComprobante(EmailReceiptRequest request) {
        String recipient = request.getTo() != null ? request.getTo().trim() : "";
        String seriesNumber = request.getSeriesNumber() != null ? request.getSeriesNumber().trim() : "B001-00000001";

        if (recipient.isEmpty()) {
            log.warn("Solicitud de envío de comprobante cancelada: No se proporcionó correo electrónico.");
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("No se proporcionó un correo electrónico válido")
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }

        log.info("Iniciando despacho de comprobante [{}] hacia [{}] a través de {}:{}...", seriesNumber, recipient, mailHost, mailPort);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String sender = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom : "ventas@univer.lat";
            helper.setFrom(sender, "MediZano Botica - Comprobante Digital");
            helper.setTo(recipient);
            helper.setSubject("Comprobante Electrónico " + seriesNumber + " - MediZano Botica");

            String htmlContent = FacturacionEmailTemplateBuilder.buildHtmlReceipt(request);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            log.info("Comprobante [{}] enviado exitosamente vía {}:{} (250 OK) a [{}]", seriesNumber, mailHost, mailPort, recipient);
            return EmailReceiptResponse.builder()
                    .success(true)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Comprobante enviado exitosamente a: " + recipient)
                    .timestamp(LocalDateTime.now().toString())
                    .build();

        } catch (Exception ex) {
            log.error("Fallo durante la conexión/envío SMTP con {}:{} para [{}]: {}", mailHost, mailPort, recipient, ex.getMessage(), ex);
            String errorDetail = ex.getMessage();
            if (errorDetail == null || errorDetail.isBlank()) {
                errorDetail = ex.getClass().getSimpleName();
            }
            return EmailReceiptResponse.builder()
                    .success(false)
                    .recipient(recipient)
                    .seriesNumber(seriesNumber)
                    .message("Error al enviar correo: " + errorDetail)
                    .timestamp(LocalDateTime.now().toString())
                    .build();
        }
    }
}
