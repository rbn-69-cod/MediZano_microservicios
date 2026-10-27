package com.medizano.inventario.service;

import com.medizano.inventario.client.dto.ProveedorDTO;
import com.medizano.inventario.entity.Batch;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Value("${app.mail.from:notificaciones@medizano.com}")
    private String defaultFrom;

    private static final String ASUNTO_CANJE = "[SOLICITUD DE CANJE] Aviso preventivo de vencimiento - MediZano Botica";

    /**
     * Envío consolidado y asíncrono de correo a laboratorio / proveedor con la lista de lotes en ventana de canje.
     */
    @Async
    public void enviarAlertaCanje(ProveedorDTO proveedor, List<Batch> lotes) {
        if (proveedor == null) {
            log.warn("No se puede enviar correo de canje: objeto Proveedor es nulo");
            return;
        }

        String destinatario = proveedor.getEmailContacto();
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("El proveedor {} (ID: {}) no tiene correo electrónico configurado. Alerta no enviada por email.",
                    proveedor.getRazonSocial(), proveedor.getId());
            return;
        }

        String remitente = (mailFrom != null && !mailFrom.isBlank())
                ? mailFrom.trim()
                : (defaultFrom != null && !defaultFrom.isBlank() ? defaultFrom.trim() : "notificaciones@medizano.com");

        try {
            log.info("Preparando correo de alerta de canje para proveedor {} ({}) con {} lotes desde remitente {}",
                    proveedor.getRazonSocial(), destinatario, lotes.size(), remitente);

            Context context = new Context();
            context.setVariable("proveedor", proveedor);
            context.setVariable("lotes", lotes);

            String contenidoHtml = templateEngine.process("mail/solicitud-canje-proveedor", context);

            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(remitente, "Botica MediZano - Gestión de Calidad");
            helper.setTo(destinatario.trim());
            helper.setSubject(ASUNTO_CANJE);
            helper.setText(contenidoHtml, true);

            mailSender.send(mensaje);
            log.info("Correo de canje enviado exitosamente a {} ({}) para {} lotes",
                    proveedor.getRazonSocial(), destinatario, lotes.size());

        } catch (Exception ex) {
            log.error("Error al enviar correo de solicitud de canje a proveedor {}: {}",
                    proveedor.getRazonSocial(), ex.getMessage(), ex);
        }
    }
}

