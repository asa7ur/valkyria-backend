package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cancela los pedidos que siguen PENDING pasado el plazo de pago y devuelve su stock.
 * <p>
 * La sesión de Stripe caduca a los 31 minutos ({@link PaymentService}) y Stripe avisa con
 * {@code checkout.session.expired}. Este job es la red de seguridad por si ese webhook no llega
 * o la sesión no llegó a crearse; por eso su plazo es algo mayor que el de Stripe.
 */
@Component
@RequiredArgsConstructor
public class OrderExpirationJob {

    private static final Logger logger = LoggerFactory.getLogger(OrderExpirationJob.class);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${app.orders.pending-expiration-minutes:35}")
    private int expirationMinutes;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void cancelExpiredOrders() {
        List<Long> expiredIds = orderRepository.findPendingOrderIdsOlderThan(expirationMinutes);
        if (expiredIds.isEmpty()) {
            return;
        }

        logger.info("Cancelando {} pedidos pendientes de pago desde hace más de {} minutos", expiredIds.size(), expirationMinutes);

        // Una transacción por pedido: si falla uno, los demás se cancelan igualmente
        for (Long orderId : expiredIds) {
            try {
                orderService.cancelPendingOrder(orderId);
            } catch (Exception e) {
                logger.error("Error al cancelar el pedido caducado #{}: {}", orderId, e.getMessage(), e);
            }
        }
    }
}
