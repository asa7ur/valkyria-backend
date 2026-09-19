package org.iesalixar.daw2.GarikAsatryan.Valkyria.orders;

import com.stripe.Stripe;
import com.stripe.net.Webhook;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.OrderCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.DocumentType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderExpirationJob;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExpirationJob orderExpirationJob;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @BeforeEach
    void mockMimeMessages() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((jakarta.mail.Session) null));
    }

    @Test
    void invalidSignature_returns400_andOrderStaysPending() throws Exception {
        TicketType type = newTicketType(5);
        Long orderId = pendingOrder(type, 1);

        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventPayload("checkout.session.completed", orderId)))
                .andExpect(status().isBadRequest());

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void malformedPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/stripe")
                        .header("Stripe-Signature", "t=1,v1=invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completedEvent_marksPaid_andSendsDocumentationOnlyOnce() throws Exception {
        TicketType type = newTicketType(5);
        Long orderId = pendingOrder(type, 2);

        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk());
        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk()); // reintento de Stripe

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        verify(mailSender, timeout(10_000)).send(any(MimeMessage.class));
        verify(mailSender, after(1_000).times(1)).send(any(MimeMessage.class));
        assertThat(stock(type)).isEqualTo(3);
    }

    /**
     * El email y el PDF se generan en segundo plano al llegar el webhook (sin petición del usuario):
     * deben salir en el idioma que tenía la web al comprar, no en el del sistema.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            en | Order Confirmation #     | Order ID:      | Attendee:  | Asistente: | MMMM d, yyyy
            es | Confirmación de Pedido # | ID del pedido: | Asistente: | Attendee:  | d 'de' MMMM 'de' yyyy
            """)
    void completedEvent_sendsEmailAndPdfInTheLanguageOfTheOrder(String language, String subjectPrefix, String emailText,
                                                                 String pdfText, String otherLanguagePdfText,
                                                                 String datePattern) throws Exception {
        TicketType type = newTicketType(5);
        type.setNameEn("Pass " + type.getName());
        ticketTypeRepository.save(type);
        Long orderId = pendingOrder(type, 1, Locale.of(language));

        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk());

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(10_000).atLeastOnce()).send(sent.capture());
        MimeMessage email = sent.getAllValues().stream()
                .filter(message -> subject(message).endsWith("#" + orderId + " - Valkyria Festival"))
                .findFirst().orElseThrow();

        assertThat(email.getSubject()).startsWith(subjectPrefix);
        assertThat(htmlBody(email)).contains(emailText);

        // p. ej. "September 19, 2026" / "19 de septiembre de 2026"
        String orderDate = orderRepository.findById(orderId).orElseThrow().getOrderDate().toLocalDate()
                .format(DateTimeFormatter.ofPattern(datePattern, Locale.of(language)));
        String pdf = pdfText(email);
        assertThat(pdf)
                .contains(pdfText, orderDate, "info@valkyriafest.es")
                .doesNotContain(otherLanguagePdfText);

        // Nombre del tipo de entrada traducido (en el PDF va en mayúsculas por CSS)
        String englishName = type.getNameEn().toLowerCase();
        if ("en".equals(language)) {
            assertThat(pdf.toLowerCase()).contains(englishName);
        } else {
            assertThat(pdf.toLowerCase()).contains(type.getName().toLowerCase()).doesNotContain(englishName);
        }
    }

    @Test
    void expiredEvent_cancelsOrder_andReleasesStock() throws Exception {
        TicketType type = newTicketType(5);
        Long orderId = pendingOrder(type, 2);
        assertThat(stock(type)).isEqualTo(3);

        sendEvent("checkout.session.expired", orderId).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock(type)).isEqualTo(5);
        assertThat(ticketStatuses(orderId)).containsOnly("CANCELLED");
    }

    @Test
    void expiredEvent_afterPayment_doesNothing() throws Exception {
        TicketType type = newTicketType(5);
        Long orderId = pendingOrder(type, 1);

        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk());
        sendEvent("checkout.session.expired", orderId).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(stock(type)).isEqualTo(4);
        // Esperar al email en segundo plano para que no se cruce con los mocks del siguiente test
        verify(mailSender, timeout(10_000)).send(any(MimeMessage.class));
    }

    @Test
    void completedEvent_forUnknownOrder_returns200() throws Exception {
        sendEvent("checkout.session.completed", 999_999L).andExpect(status().isOk());
    }

    @Test
    void expirationJob_cancelsOnlyOldPendingOrders() {
        TicketType type = newTicketType(5);
        Long oldOrderId = pendingOrder(type, 2);
        Long recentOrderId = pendingOrder(type, 1);
        jdbcTemplate.update("UPDATE orders SET order_date = NOW() - INTERVAL 1 HOUR WHERE id = ?", oldOrderId);

        orderExpirationJob.cancelExpiredOrders();

        assertThat(orderStatus(oldOrderId)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderStatus(recentOrderId)).isEqualTo(OrderStatus.PENDING);
        assertThat(stock(type)).isEqualTo(4);
    }

    @Test
    void latePayment_ofCancelledOrder_reactivatesItWhenStockIsAvailable() throws Exception {
        TicketType type = newTicketType(5);
        Long orderId = pendingOrder(type, 2);
        orderService.cancelPendingOrder(orderId);

        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.PAID);
        assertThat(stock(type)).isEqualTo(3);
        assertThat(ticketStatuses(orderId)).containsOnly("ACTIVE");
        verify(mailSender, timeout(10_000)).send(any(MimeMessage.class));
    }

    @Test
    void latePayment_ofCancelledOrder_staysCancelledWithoutStock() throws Exception {
        TicketType type = newTicketType(2);
        Long orderId = pendingOrder(type, 2);
        orderService.cancelPendingOrder(orderId);
        pendingOrder(type, 1); // alguien compra mientras tanto: solo queda 1

        sendEvent("checkout.session.completed", orderId).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stock(type)).isEqualTo(1);
        verify(mailSender, after(1_000).never()).send(any(MimeMessage.class));
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private ResultActions sendEvent(String eventType, Long orderId) throws Exception {
        String payload = eventPayload(eventType, orderId);
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = Webhook.Util.computeHmacSha256(webhookSecret, timestamp + "." + payload);

        return mockMvc.perform(post("/api/v1/webhooks/stripe")
                .header("Stripe-Signature", "t=" + timestamp + ",v1=" + signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private String eventPayload(String eventType, Long orderId) {
        return """
                {"id":"evt_%s","object":"event","api_version":"%s","type":"%s",
                 "data":{"object":{"id":"cs_test_%s","object":"checkout.session","client_reference_id":"%d"}}}"""
                .formatted(UUID.randomUUID(), Stripe.API_VERSION, eventType, UUID.randomUUID(), orderId);
    }

    // Pedido hecho con la web en ese idioma (el que llegaría en Accept-Language)
    private Long pendingOrder(TicketType type, int quantity, Locale webLanguage) {
        LocaleContextHolder.setLocale(webLanguage);
        try {
            return pendingOrder(type, quantity);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    private static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String htmlBody(MimeMessage message) throws Exception {
        for (BodyPart part : parts(message.getContent())) {
            if (part.getFileName() == null && part.getContent() instanceof String html) {
                return html;
            }
        }
        throw new AssertionError("El email no tiene cuerpo HTML");
    }

    private static String pdfText(MimeMessage message) throws Exception {
        for (BodyPart part : parts(message.getContent())) {
            if (part.getFileName() != null && part.getFileName().endsWith(".pdf")) {
                try (PDDocument document = Loader.loadPDF(part.getInputStream().readAllBytes())) {
                    return new PDFTextStripper().getText(document);
                }
            }
        }
        throw new AssertionError("El email no lleva el PDF adjunto");
    }

    // Partes "hoja" de un email multipart (el cuerpo HTML y los adjuntos)
    private static List<BodyPart> parts(Object content) throws Exception {
        List<BodyPart> result = new ArrayList<>();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.getContent() instanceof Multipart nested) {
                    result.addAll(parts(nested));
                } else {
                    result.add(part);
                }
            }
        }
        return result;
    }

    private Long pendingOrder(TicketType type, int quantity) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setGuestEmail("guest@valkyria.test");
        request.setTickets(IntStream.range(0, quantity)
                .mapToObj(i -> new TicketCreateDTO("Test", "Buyer", DocumentType.DNI, "12345678Z", LocalDate.of(1990, 1, 1), type.getId()))
                .toList());
        return orderService.executeOrder(request, null).getId();
    }

    private TicketType newTicketType(int stock) {
        TicketType type = new TicketType();
        type.setName("Test " + UUID.randomUUID().toString().substring(0, 8));
        type.setPrice(new BigDecimal("10.00"));
        type.setStockTotal(stock);
        type.setStockAvailable(stock);
        return ticketTypeRepository.save(type);
    }

    private int stock(TicketType type) {
        return ticketTypeRepository.findById(type.getId()).orElseThrow().getStockAvailable();
    }

    private OrderStatus orderStatus(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow().getStatus();
    }

    private List<String> ticketStatuses(Long orderId) {
        return jdbcTemplate.queryForList("SELECT status FROM tickets WHERE order_id = ?", String.class, orderId);
    }
}
