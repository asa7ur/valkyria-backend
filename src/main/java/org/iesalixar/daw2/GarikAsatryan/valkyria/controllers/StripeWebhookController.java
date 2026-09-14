package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import com.stripe.exception.SignatureVerificationException;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentService paymentService;

    /**
     * Firma o payload inválidos → 400 (Stripe no reintenta). Cualquier otro error llega al handler global
     * como 500 y Stripe reintenta el envío más tarde.
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        try {
            paymentService.processWebhookEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            logger.warn("Webhook de Stripe rechazado: firma inválida");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        return ResponseEntity.ok("OK");
    }
}
