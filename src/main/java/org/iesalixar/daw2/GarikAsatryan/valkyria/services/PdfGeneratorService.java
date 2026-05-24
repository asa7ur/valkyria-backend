package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Camping;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Ticket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PdfGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(PdfGeneratorService.class);

    private final QrCodeService qrCodeService;
    private final TemplateEngine templateEngine;

    public byte[] generateOrderPdf(Order order) throws Exception {
        logger.info("Generando PDF profesional para pedido #{}", order.getId());

        // 1. Preparar datos
        String customerName = (order.getUser() != null)
                ? order.getUser().getFirstName() + " " + order.getUser().getLastName()
                : "Invitado (" + order.getGuestEmail() + ")";

        Map<String, String> qrMap = new HashMap<>();

        for (Ticket t : order.getTickets()) {
            byte[] qrBytes = qrCodeService.generateQrCodeImage(t.getQrCode());
            qrMap.put("T" + t.getId(), Base64.getEncoder().encodeToString(qrBytes));
        }

        for (Camping c : order.getCampings()) {
            byte[] qrBytes = qrCodeService.generateQrCodeImage(c.getQrCode());
            qrMap.put("C" + c.getId(), Base64.getEncoder().encodeToString(qrBytes));
        }

        // 2. Contexto Thymeleaf
        Context context = new Context();
        context.setVariable("order", order);
        context.setVariable("customerName", customerName);
        context.setVariable("qrMap", qrMap);

        // 3. Renderizado HTML
        String htmlContent = templateEngine.process("pdf-order-tickets", context);

        // 4. Construcción del PDF
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, "/");
            builder.toStream(outputStream);
            builder.run();

            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.error("Error en la generación del PDF: {}", e.getMessage());
            throw new RuntimeException("Error visual en PDF", e);
        }
    }
}