package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Servicio de integración con Stripe para procesamiento de pagos.
 * Gestiona el ciclo completo de pago: creación de sesiones, webhooks y confirmaciones.
 * <p>
 * Flujo de pago:
 * 1. Usuario completa el pedido → createStripeSession()
 * 2. Usuario redirigido a Stripe Checkout
 * 3. Usuario paga en Stripe
 * 4. Stripe envía webhook → processWebhookEvent()
 * 5. Sistema confirma pedido y envía documentación
 * <p>
 * IMPORTANTE: Este servicio implementa "Soft Fail" para procesos secundarios
 * (generación PDF, envío email) para no comprometer la confirmación del pago.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final OrderService orderService;
    private final PdfGeneratorService pdfGeneratorService;
    private final EmailService emailService;
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
     * Este método se ejecuta automáticamente una vez después de la inyección de dependencias.
     * Configura la API key global para todas las llamadas posteriores a Stripe.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        logger.info("Stripe API inicializada correctamente con la secret key configurada");
    }

    /**
     * Crea una sesión de pago (Checkout Session) en Stripe.
     * Redirige al usuario a la página de pago de Stripe con la sesión creada.
     * <p>
     * Proceso:
     * 1. Configura el método de pago (tarjeta)
     * 2. Establece el modo de pago único (no suscripción)
     * 3. Asocia el ID del pedido como referencia
     * 4. Define URLs de retorno (éxito/cancelación)
     * 5. Crea el item de línea con el precio total
     * 6. Invoca la API de Stripe para crear la sesión
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
        logger.debug("Precio del pedido: {} EUR", order.getTotalPrice());

        // Conversión de precio a céntimos para Stripe
        long amountInCents = order.getTotalPrice().movePointRight(2).longValue();
        logger.debug("Precio convertido para Stripe: {} céntimos", amountInCents);

        // Construcción de parámetros de la sesión de Checkout
        SessionCreateParams params = SessionCreateParams.builder()
                // Método de pago aceptado: solo tarjeta en este caso
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)

                // Modo de pago: PAYMENT (pago único) vs SUBSCRIPTION
                .setMode(SessionCreateParams.Mode.PAYMENT)

                // ID de referencia para asociar la sesión con nuestro pedido
                // Esto nos permite identificar el pedido en el webhook
                .setClientReferenceId(order.getId().toString())

                // URLs de retorno tras completar o cancelar el pago
                // {CHECKOUT_SESSION_ID} es reemplazado por Stripe con el ID real
                .setSuccessUrl(appUrl + "/purchase/success?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(appUrl + "/purchase/cancel")

                // Item de línea: lo que el usuario está comprando
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L) // 1 pedido completo
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur") // Moneda europea
                                                .setUnitAmount(amountInCents) // Precio en céntimos
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

        logger.debug("Parámetros de sesión construidos. Invocando Stripe API...");

        // Llamada a la API de Stripe para crear la sesión
        Session session = Session.create(params);

        logger.info("✓ Sesión de Stripe creada exitosamente. Session ID: {}, URL: {}",
                session.getId(), session.getUrl());

        // Retornar la URL donde redirigir al usuario
        return session.getUrl();
    }

    /**
     * Procesa eventos de webhook enviados por Stripe cuando ocurren eventos importantes.
     * Este método es invocado por el controlador que recibe POST requests de Stripe.
     * <p>
     * Flujo de seguridad:
     * 1. Verifica la firma del webhook para autenticidad
     * 2. Deserializa el evento
     * 3. Si es "checkout.session.completed" → confirma el pago
     * 4. Extrae el ID del pedido de la sesión
     * 5. Actualiza el estado del pedido en BD (CRITICAL)
     * 6. Intenta enviar documentación (BEST EFFORT)
     * <p>
     * ESTRATEGIA "SOFT FAIL":
     * - La confirmación del pago SIEMPRE se ejecuta y persiste
     * - El envío de PDF/email es secundario y falla silenciosamente si hay problemas
     * - Esto evita perder pagos confirmados por problemas de email/PDF
     *
     * @param payload   JSON del evento enviado por Stripe (cuerpo raw del request)
     * @param sigHeader Header 'Stripe-Signature' para validar autenticidad
     * @throws Exception si la firma es inválida o hay error crítico
     */
    @Transactional
    public void processWebhookEvent(String payload, String sigHeader) throws Exception {
        logger.info("Procesando evento de webhook de Stripe");
        logger.debug("Payload recibido (primeros 100 chars): {}",
                payload.substring(0, Math.min(100, payload.length())).replaceAll("[\r\n]", "_"));

        // Paso 1: Construir y verificar el evento usando la firma
        // Esto garantiza que el webhook realmente viene de Stripe y no ha sido manipulado
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            logger.debug("Evento verificado exitosamente. Tipo: {}", event.getType());
        } catch (Exception e) {
            logger.error("Firma de webhook inválida. Posible intento de fraude: {}", e.getMessage());
            throw e; // Re-lanzar para que el controlador retorne 400
        }

        // Paso 2: Procesar solo eventos de sesiones completadas
        if ("checkout.session.completed".equals(event.getType())) {
            logger.info("Evento de tipo 'checkout.session.completed' detectado");

            // Paso 3: Extraer el ID del pedido de la sesión
            String orderIdStr = extractOrderId(event);

            if (orderIdStr != null) {
                logger.debug("ID de pedido extraído de la sesión: {}", orderIdStr);

                try {
                    Long orderId = Long.parseLong(orderIdStr);

                    // 1. CONFIRMACIÓN (Crítico)
                    Order order = orderService.confirmPayment(orderId);
                    logger.info("✓ Pago confirmado en DB para pedido #{}", orderId);

                    // 2. DOCUMENTACIÓN (No crítico para Stripe)
                    try {
                        // Llamada al método. Si quieres que sea Async de verdad,
                        // muévelo a otro Service o asegúrate de tener @EnableAsync
                        sendDocumentation(order);
                    } catch (Exception e) {
                        // LOGUEAMOS EL ERROR PERO NO LANZAMOS EXCEPCIÓN
                        // Así el webhook responderá 200 OK a Stripe aunque falle el email
                        logger.error("Error enviando documentos, pero el pago ya está marcado: {}", e.getMessage());
                    }

                } catch (NumberFormatException e) {
                    logger.error("ID de pedido inválido: {}", orderIdStr);
                }
            } else {
                logger.warn("No se pudo extraer el ID del pedido del evento de Stripe");
            }
        } else {
            logger.debug("Evento de tipo '{}' ignorado (solo procesamos checkout.session.completed)",
                    event.getType());
        }
    }

    /**
     * Extrae el ID del pedido del objeto Session de Stripe.
     * Intenta dos métodos para máxima compatibilidad:
     * 1. Deserialización directa del objeto Session
     * 2. Parsing manual del JSON raw si falla el método 1
     * <p>
     * El ID del pedido se almacenó en 'client_reference_id' al crear la sesión.
     *
     * @param event Evento de Stripe que contiene los datos de la sesión
     * @return ID del pedido como String, o null si no se puede extraer
     */
    private String extractOrderId(Event event) {
        logger.debug("Extrayendo ID de pedido del evento de Stripe");

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();

        // Método 1: Deserialización automática del objeto Session
        return dataObjectDeserializer.getObject()
                .map(stripeObject -> {
                    Session session = (Session) stripeObject;
                    String clientRefId = session.getClientReferenceId();
                    logger.debug("ID extraído mediante deserialización: {}", clientRefId != null ? clientRefId.replaceAll("[\r\n]", "_") : null);
                    return clientRefId;
                })
                .orElseGet(() -> {
                    // Método 2: Fallback - parsing manual del JSON raw
                    logger.debug("Deserialización automática falló, usando parsing manual del JSON");
                    try {
                        JsonNode node = objectMapper.readTree(dataObjectDeserializer.getRawJson());

                        if (node.has("client_reference_id")) {
                            String clientRefId = node.get("client_reference_id").stringValue();
                            logger.debug("ID extraído mediante parsing JSON: {}", clientRefId.replaceAll("[\r\n]", "_"));
                            return clientRefId;
                        } else {
                            logger.warn("Campo 'client_reference_id' no encontrado en JSON raw");
                            return null;
                        }
                    } catch (Exception e) {
                        logger.error("Error al parsear JSON raw del evento: {}", e.getMessage(), e);
                        return null;
                    }
                });
    }

    /**
     * Lógica encapsulada de envío de documentación (PDF + Email).
     * Este método implementa "Soft Fail" - captura todas las excepciones
     * para no comprometer la confirmación del pago que ya ocurrió.
     * <p>
     * Si falla el envío, el pedido sigue siendo válido (PAID) y el usuario
     * puede solicitar el PDF manualmente desde su panel de pedidos.
     * <p>
     * Proceso:
     * 1. Genera el PDF del pedido con todos los tickets y campings
     * 2. Envía email con el PDF adjunto
     * 3. Registra el éxito o fallo en logs
     *
     * @param order Pedido para el cual generar y enviar documentación
     */
    @Async
    public void sendDocumentation(Order order) {
        logger.info("Iniciando envío de documentación para pedido #{}", order.getId());

        try {
            // Paso 1: Generar el PDF del pedido
            logger.debug("Generando PDF del pedido #{}...", order.getId());
            byte[] pdfBytes = pdfGeneratorService.generateOrderPdf(order);
            logger.debug("PDF generado correctamente ({} bytes)", pdfBytes.length);

            // Paso 2: Enviar email con el PDF adjunto
            String recipientEmail = (order.getUser() != null)
                    ? order.getUser().getEmail()
                    : order.getGuestEmail();

            logger.debug("Enviando email con PDF a: {}", recipientEmail != null ? recipientEmail.replaceAll("[\r\n]", "_") : null);
            emailService.sendOrderConfirmationEmail(order, pdfBytes);

            logger.info("✓ Documentación enviada exitosamente por email a: {}", recipientEmail != null ? recipientEmail.replaceAll("[\r\n]", "_") : null);

        } catch (Exception e) {
            // SOFT FAIL: Solo registramos el error, no propagamos la excepción
            // El pedido ya está confirmado (PAID), lo cual es lo más importante
            logger.error("⚠ Error al enviar documentación del pedido #{} (El pago SÍ fue confirmado). " +
                            "El usuario puede descargar el PDF desde su panel. Error: {}",
                    order.getId(), e.getMessage(), e);

            // Aquí podríamos implementar lógica adicional como:
            // - Marcar el pedido para reintento de envío
            // - Enviar notificación a administradores
            // - Almacenar en una cola de reintentos
        }
    }
}