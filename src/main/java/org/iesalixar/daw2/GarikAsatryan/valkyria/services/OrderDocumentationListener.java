package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.config.LocaleConfig;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.events.OrderPaidEvent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;

/**
 * Genera el PDF y envía el email de un pedido pagado.
 * <p>
 * - AFTER_COMMIT: solo se ejecuta si el cambio a PAID se ha guardado.
 * - @Async: en otro hilo, así el webhook responde a Stripe sin esperar al PDF ni al servidor SMTP.
 * - Transacción propia de solo lectura: el PDF recorre relaciones LAZY (usuario, tickets, campings).
 * <p>
 * Los errores solo se registran: el pedido ya está pagado y el usuario puede descargar el PDF desde su panel.
 */
@Component
@RequiredArgsConstructor
public class OrderDocumentationListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderDocumentationListener.class);

    private final OrderRepository orderRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onOrderPaid(OrderPaidEvent event) {
        Long orderId = event.orderId();

        try {
            Order order = orderRepository.findById(orderId).orElse(null);
            if (order == null) {
                logger.warn("No se envía la documentación del pedido #{}: ya no existe", orderId);
                return;
            }

            // El idioma de la web al comprar: este hilo no tiene petición y usaría el del sistema operativo
            Locale locale = LocaleConfig.supportedOrDefault(order.getLanguage());
            byte[] pdfBytes = pdfGeneratorService.generateOrderPdf(order, locale);
            emailService.sendOrderConfirmationEmail(order, pdfBytes, locale);
            logger.info("✓ Documentación del pedido #{} enviada", orderId);
        } catch (Exception e) {
            logger.error("⚠ Error al enviar la documentación del pedido #{} (el pago SÍ está confirmado): {}",
                    orderId, e.getMessage(), e);
        }
    }
}
