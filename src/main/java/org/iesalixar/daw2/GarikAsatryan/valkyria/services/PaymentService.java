package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Servicio de integración con Stripe para procesamiento de pagos.
 * <p>
 * Flujo de pago:
 * 1. Usuario completa el pedido → createStripeSession()
 * 2. Usuario redirigido a Stripe Checkout (la sesión caduca a los {@link #SESSION_TTL})
 * 3. Stripe envía webhook → processWebhookEvent()
 *    - checkout.session.completed → el pedido pasa a PAID y se envía la documentación en segundo plano
 *    - checkout.session.expired   → el pedido se cancela y se devuelve el stock
 * <p>
 * El webhook no es transaccional: cada operación de {@link OrderService} tiene su propia transacción,
 * y el PDF y el email los envía {@link OrderDocumentationListener} cuando esta ya se ha confirmado.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    // Stripe exige un mínimo de 30 minutos; el job de caducidad usa un plazo algo mayor
    static final Duration SESSION_TTL = Duration.ofMinutes(31);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final OrderService orderService;
    private final ObjectMapper objectMapper; // Para parsear JSON de Stripe

    // Credenciales de Stripe desde application.properties
    @Value("${stripe.secret.key}")
    private String secretKey;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    // URL base de la aplicación para redirecciones
    @Value("${app.url}")
    private String appUrl;

    /**
     * Inicializa la configuración de Stripe tras la construcción del bean.
     * Configura la API key global para todas las llamadas posteriores a Stripe.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("Stripe API inicializada correctamente con la secret key configurada");
    }

    /**
     * Crea una sesión de pago (Checkout Session) en Stripe.
     * <p>
     * IMPORTANTE sobre precios:
     * - Stripe trabaja en céntimos (o unidad mínima de la moneda)
     * - Por eso usamos movePointRight(2) para convertir 19.99€ → 1999 céntimos
     *
     * @param order Pedido para el cual crear la sesión de pago
     * @return URL de la sesión de Stripe donde redirigir al usuario
     * @throws Exception si hay error en la comunicación con Stripe API
     */
    public String createStripeSession(Order order) throws Exception {
        logger.info("Iniciando creación de sesión de Stripe para pedido #{}", order.getId());

        // Conversión de precio a céntimos para Stripe
        long amountInCents = order.getTotalPrice().movePointRight(2).longValue();
        logger.debug("Precio convertido para Stripe: {} céntimos", amountInCents);

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)

                // ID de referencia para identificar el pedido en el webhook
                .setClientReferenceId(order.getId().toString())

                // Pasado este tiempo Stripe no acepta el pago y envía checkout.session.expired
                .setExpiresAt(Instant.now().plus(SESSION_TTL).getEpochSecond())

                // {CHECKOUT_SESSION_ID} es reemplazado por Stripe con el ID real
                .setSuccessUrl(appUrl + "/purchase/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(appUrl + "/purchase/cancel")

                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L) // 1 pedido completo
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Pedido Valkyria #" + order.getId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        Session session = Session.create(params);

        logger.info("✓ Sesión de Stripe creada. Session ID: {}", session.getId());
        return session.getUrl();
    }

    /**
     * Procesa un evento de webhook enviado por Stripe.
     * <p>
     * Si la firma no es válida se lanza {@link SignatureVerificationException} (el controlador responde 400).
     * Cualquier otro error se propaga (500) para que Stripe reintente el envío; los reintentos son seguros
     * porque confirmar y cancelar son operaciones idempotentes.
     *
     * @param payload   JSON del evento enviado por Stripe (cuerpo raw del request)
     * @param sigHeader Header 'Stripe-Signature' para validar autenticidad
     */
    public void processWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        // Garantiza que el webhook viene de Stripe y no ha sido manipulado. Se verifica la firma antes de
        // parsear: constructEvent parsea primero y un cuerpo que no es JSON acabaría en 500 en vez de 400
        Webhook.Signature.verifyHeader(payload, sigHeader, endpointSecret, Webhook.DEFAULT_TOLERANCE);
        Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        logger.info("Webhook de Stripe recibido: {} ({})", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> withOrderId(event, orderService::confirmPayment);
            case "checkout.session.expired" -> withOrderId(event, orderService::cancelPendingOrder);
            default -> logger.debug("Evento de tipo '{}' ignorado", event.getType());
        }
    }

    private void withOrderId(Event event, Consumer<Long> action) {
        String orderIdStr = extractOrderId(event);
        if (orderIdStr == null) {
            logger.warn("No se pudo extraer el ID del pedido del evento de Stripe {}", event.getId());
            return;
        }

        try {
            action.accept(Long.parseLong(orderIdStr));
        } catch (NumberFormatException e) {
            logger.warn("ID de pedido inválido en el evento de Stripe {}: {}", event.getId(), orderIdStr.replaceAll("[\r\n]", "_"));
        }
    }

    /**
     * Extrae el ID del pedido ('client_reference_id') del objeto Session de Stripe.
     * Si la versión de la API del evento no coincide con la de la librería, la deserialización
     * automática falla y se lee el JSON raw.
     *
     * @param event Evento de Stripe que contiene los datos de la sesión
     * @return ID del pedido como String, o null si no se puede extraer
     */
    private String extractOrderId(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        return dataObjectDeserializer.getObject()
                .map(stripeObject -> ((Session) stripeObject).getClientReferenceId())
                .orElseGet(() -> {
                    logger.debug("Deserialización automática falló, usando parsing manual del JSON");
                    try {
                        JsonNode node = objectMapper.readTree(dataObjectDeserializer.getRawJson());
                        JsonNode clientRefId = node.get("client_reference_id");
                        return clientRefId != null && !clientRefId.isNull() ? clientRefId.asString() : null;
                    } catch (Exception e) {
                        logger.error("Error al parsear JSON raw del evento: {}", e.getMessage(), e);
                        return null;
                    }
                });
    }
}
