package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.OrderCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.OrderDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.PaymentService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final MessageSource messageSource;

    /**
     * Devuelve todos los pedidos
     */
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<List<OrderDTO>>> getAllOrders(@ModelAttribute FilterDTO filterDTO) {
        List<OrderDTO> data = orderService.getAllOrders(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.order.list.success"), data, filterDTO));
    }

    /**
     * Devuelve el historial de pedidos del usuario autenticado.
     */
    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO<List<OrderDTO>>> getMyOrders(Authentication authentication) {
        // authentication.getName() devuelve el email del usuario logueado (desde el JWT)
        List<OrderDTO> orders = orderService.getOrdersByUser(authentication.getName());
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.order.history.success"), orders));
    }

    /**
     * Borra un pedido.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.order.delete.success"), null));
    }

    /**
     * Proceso de Checkout:
     * 1. Crea el pedido en estado PENDING y descuenta stock.
     * 2. Crea la sesión en Stripe.
     * 3. Devuelve la URL de Stripe para que Angular redireccione al usuario.
     */
    @PostMapping
    public ResponseEntity<ResponseDTO<Map<String, String>>> checkout(
            @Valid @RequestBody OrderCreateDTO request,
            Authentication authentication) throws Exception {

        User user = null;
        // Si hay autenticación, buscamos al usuario. Si no, se queda como null.
        if (authentication != null && authentication.isAuthenticated()) {
            user = userService.getUserByEmailEntity(authentication.getName());
        }

        // Ejecutamos la lógica de creación de pedido y stock
        Order order = orderService.executeOrder(request, user);

        // Generamos la pasarela de pago
        String stripeUrl = paymentService.createStripeSession(order);

        // Devolvemos la URL para que el frontend haga: window.location.href = res.url;
        return ResponseEntity.ok(ResponseDTO.success(
                getMessage("msg.order.checkout.success"),
                Map.of("url", stripeUrl)
        ));
    }

    /**
     * Descarga del PDF de credenciales.
     * Solo permite la descarga si el pedido pertenece al usuario autenticado.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, Authentication authentication) throws Exception {
        byte[] pdfBytes = orderService.generateOrderPdfForUser(id, authentication.getName());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Valkyria_Pedido_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}