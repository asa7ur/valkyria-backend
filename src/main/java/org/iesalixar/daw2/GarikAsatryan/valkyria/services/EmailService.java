package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final TemplateEngine templateEngine;

    @Value("${app.url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String mailFrom;

    /**
     * Envía un correo de activación utilizando el template HTML de Thymeleaf.
     */
    public void sendRegistrationConfirmationEmail(String to, String firstName, String token) {
        logger.info("Iniciando envío de correo de activación (HTML) a: {}", to);

        // El enlace de activación debe llevar al usuario a Angular
        String confirmationUrl = frontendUrl + "/confirm-registration?token=" + token;

        String logoUrl = getLogoAsBase64();

        // 2. Preparar el contexto de Thymeleaf (las variables que usa el HTML)
        // context.setVariable("nombre_en_html", valor_en_java)
        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariable("firstName", firstName);
        context.setVariable("activationUrl", confirmationUrl);
        context.setVariable("logoUrl", logoUrl);

        // 3. CProcesar el archivo HTML
        // "email-activation" debe ser el nombre del archivo .html en src/main/resources/templates/
        String body = templateEngine.process("email-activation", context);
        logger.debug("Plantilla HTML procesada correctamente");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);

            helper.setTo(to);
            // El asunto se sigue obteniendo de messages.properties
            helper.setSubject(getMessage("msg.register.email.subject", null));
            helper.setText(body, true); // true = enviar como HTML

            mailSender.send(message);
            logger.info("Correo de activación HTML enviado a: {}", to);
        } catch (Exception e) {
            logger.error("Error al enviar correo de activación: {}", e.getMessage());
            throw new RuntimeException("Error al enviar email de activación", e);
        }
    }

    /**
     * Este método se mantiene igual (usa texto plano de properties)
     * a menos que crees otro template HTML para los pedidos.
     */
    public void sendOrderConfirmationEmail(Order order, byte[] pdfBytes) throws Exception {
        logger.info("Iniciando envío de confirmación de pedido HTML #{}", order.getId());

        String to = (order.getUser() != null) ? order.getUser().getEmail() : order.getGuestEmail();
        String firstName = (order.getUser() != null) ? order.getUser().getFirstName() : "Invitado";
        String logoUrl = getLogoAsBase64();

        // 1. Preparar el contexto de Thymeleaf
        Context context = new Context(LocaleContextHolder.getLocale());
        context.setVariable("firstName", firstName);
        context.setVariable("orderId", order.getId());
        context.setVariable("logoUrl", logoUrl);

        // 2. Procesar el template HTML
        String body = templateEngine.process("email-order-confirmation", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);

            helper.setTo(to);
            helper.setSubject(getMessage("msg.order.email.subject", new Object[]{order.getId()}));
            helper.setText(body, true); // true = enviar como HTML

            String fileName = "Valkyria_Ticket_Order_" + order.getId() + ".pdf";
            helper.addAttachment(fileName, new ByteArrayResource(pdfBytes));

            mailSender.send(message);
            logger.info("Confirmación de pedido HTML #{} enviada.", order.getId());
        } catch (Exception e) {
            logger.error("Error al enviar correo de pedido: {}", e.getMessage());
            throw new RuntimeException("Error al enviar email de confirmación", e);
        }
    }

    private String getMessage(String key, Object[] args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    private String getLogoAsBase64() {
        try {
            byte[] bytes = new ClassPathResource("logo.png").getInputStream().readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            logger.warn("No se pudo cargar el logo desde el classpath: {}", e.getMessage());
            return "";
        }
    }
}