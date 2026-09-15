package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.events.OrderPaidEvent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.CampingMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.OrderMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.TicketMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de pedidos (orders).
 * Orquesta el proceso completo de compra de entradas y reservas de camping.
 * <p>
 * Responsabilidades principales:
 * - Reserva atómica de stock (vía {@link StockService})
 * - Cálculo de precios totales
 * - Generación de códigos QR únicos para tickets y campings
 * - Soporte para usuarios registrados e invitados
 * - Confirmación de pagos tras notificación de Stripe
 * <p>
 * IMPORTANTE: Los métodos de creación de pedidos son transaccionales para garantizar
 * atomicidad (todo o nada) y consistencia del stock.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    // Campos por los que se puede ordenar el listado paginado
    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "orderDate", "totalPrice", "status");

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CampingTypeRepository campingTypeRepository;
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final CampingMapper campingMapper;
    private final PaginationComponent paginationComponent;
    private final PdfGeneratorService pdfGeneratorService;
    private final StockService stockService;
    private final QrCodeService qrCodeService;
    private final TicketRepository ticketRepository;
    private final CampingRepository campingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<OrderDTO> getAllOrders(FilterDTO filterDTO) {
        logger.info("Recuperando pedidos. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch().replaceAll("[\r\n]", "_") : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id", SORTABLE_FIELDS);

        Page<Order> orderPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isEmpty())
                ? orderRepository.searchOrders(filterDTO.getSearch(), pageable)
                : orderRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, orderPage);

        logger.debug("Pedidos encontrados: {} de {} totales",
                orderPage.getNumberOfElements(),
                orderPage.getTotalElements());

        // Convertir entidades a DTOs de respuesta
        return orderPage.getContent().stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el historial completo de pedidos de un usuario específico.
     * Los pedidos se ordenan por fecha descendente (más recientes primero).
     * Utilizado en la sección "Mis Pedidos" del perfil de usuario.
     *
     * @param email Email del usuario
     * @return Lista de DTOs con todos los pedidos del usuario ordenados cronológicamente
     */
    @Transactional(readOnly = true)
    public List<OrderDTO> getOrdersByUser(String email) {
        logger.info("Recuperando historial de pedidos para usuario: {}", email.replaceAll("[\r\n]", "_"));

        // Consulta optimizada con ordenación en BD
        List<Order> orders = orderRepository.findByUserEmailOrderByOrderDateDesc(email);

        logger.debug("Total de pedidos encontrados para {}: {}", email.replaceAll("[\r\n]", "_"), orders.size());

        return orderMapper.toDTOList(orders);
    }

    /**
     * Busca un pedido por ID y lo devuelve como entidad (no DTO).
     * Método de uso interno para otros servicios que necesitan la entidad completa.
     *
     * @param id ID del pedido
     * @return Entidad Order completa
     * @throws AppException si el pedido no existe
     */
    public Order getOrderEntityById(Long id) {
        logger.debug("Buscando entidad Order con ID: {}", id);

        return orderRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Pedido con ID {} no encontrado", id);
                    return AppException.notFound("msg.error.order-not-found", id);
                });
    }

    // Transaccional porque la comprobación y el PDF recorren relaciones LAZY (usuario, tickets, campings)
    @Transactional(readOnly = true)
    public byte[] generateOrderPdfForUser(Long id, String email) throws Exception {
        Order order = getOrderEntityById(id);

        if (order.getUser() == null || !order.getUser().getEmail().equals(email)) {
            throw AppException.forbidden("msg.error.unauthorized-access");
        }

        return pdfGeneratorService.generateOrderPdf(order);
    }

    /**
     * Elimina un pedido por su ID y devuelve su stock (salvo que ya estuviera cancelado,
     * porque al cancelarlo ya se devolvió).
     *
     * @param id ID del pedido a eliminar.
     * @throws AppException Si el pedido no existe.
     */
    @Transactional
    public void deleteOrder(Long id) {
        Order order = getOrderEntityById(id);

        if (order.getStatus() != OrderStatus.CANCELLED) {
            stockService.releaseOrderStock(order);
        }

        orderRepository.delete(order);
        logger.info("✓ Pedido #{} eliminado y su stock restaurado", id);
    }

    /**
     * PROCESO PRINCIPAL DE COMPRA - Crea un pedido PENDING reservando el stock.
     * <p>
     * 1. Valida el email de contacto (obligatorio para invitados)
     * 2. Reserva stock por tipo con UPDATE atómicos (sin sobreventa con compras simultáneas)
     * 3. Crea tickets y campings con su código QR
     * 4. Calcula el precio total y persiste el pedido
     * <p>
     * TRANSACCIONAL: si falta stock en cualquier tipo se revierten también las reservas ya hechas.
     *
     * @param request DTO con los items a comprar (tickets y/o campings)
     * @param user    Usuario registrado (null para compras de invitado)
     * @return Pedido creado con estado PENDING (pendiente de pago)
     * @throws AppException si falta stock, un tipo no existe o falta el email del invitado
     */
    @Transactional
    public Order executeOrder(OrderCreateDTO request, User user) {
        List<TicketCreateDTO> ticketRequests = request.getTickets() != null ? request.getTickets() : List.of();
        List<CampingCreateDTO> campingRequests = request.getCampings() != null ? request.getCampings() : List.of();

        if (user == null && (request.getGuestEmail() == null || request.getGuestEmail().isBlank())) {
            throw AppException.badRequest("msg.order.guest-email-required");
        }

        logger.info("Procesando pedido de {} ({} tickets, {} campings)",
                user != null ? user.getEmail().replaceAll("[\r\n]", "_") : "invitado",
                ticketRequests.size(), campingRequests.size());

        Order order = new Order();
        order.setUser(user);
        order.setGuestEmail(user == null ? request.getGuestEmail() : null);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal totalPrice = BigDecimal.ZERO;

        // ========== TICKETS: reservar stock por tipo y crear las entradas ==========
        Map<Long, TicketType> ticketTypes = new HashMap<>();
        ticketRequests.stream()
                .collect(Collectors.groupingBy(TicketCreateDTO::getTicketTypeId, Collectors.counting()))
                .forEach((typeId, count) -> {
                    TicketType type = ticketTypeRepository.findById(typeId)
                            .orElseThrow(() -> AppException.badRequest("msg.error.ticket-type-not-found"));
                    stockService.reserveTickets(type, count.intValue());
                    ticketTypes.put(typeId, type);
                });

        for (TicketCreateDTO ticketRequest : ticketRequests) {
            TicketType type = ticketTypes.get(ticketRequest.getTicketTypeId());
            order.getTickets().add(ticketMapper.toEntityFromOrder(ticketRequest, type, order, qrCodeService.newTicketCode()));
            totalPrice = totalPrice.add(type.getPrice());
        }

        // ========== CAMPINGS: reservar stock por tipo y crear las reservas ==========
        Map<Long, CampingType> campingTypes = new HashMap<>();
        campingRequests.stream()
                .collect(Collectors.groupingBy(CampingCreateDTO::getCampingTypeId, Collectors.counting()))
                .forEach((typeId, count) -> {
                    CampingType type = campingTypeRepository.findById(typeId)
                            .orElseThrow(() -> AppException.badRequest("msg.error.camping-type-not-found"));
                    stockService.reserveCampings(type, count.intValue());
                    campingTypes.put(typeId, type);
                });

        for (CampingCreateDTO campingRequest : campingRequests) {
            CampingType type = campingTypes.get(campingRequest.getCampingTypeId());
            order.getCampings().add(campingMapper.toEntityFromOrder(campingRequest, type, order, qrCodeService.newCampingCode()));
            totalPrice = totalPrice.add(type.getPrice());
        }

        // ========== FINALIZACIÓN DEL PEDIDO ==========
        order.setTotalPrice(totalPrice);
        Order savedOrder = orderRepository.save(order);

        logger.info("✓ Pedido #{} creado. Total: {} €, Items: {} tickets + {} campings",
                savedOrder.getId(), savedOrder.getTotalPrice(),
                savedOrder.getTickets().size(), savedOrder.getCampings().size());

        return savedOrder;
    }

    /**
     * Confirma el pago de un pedido tras el webhook {@code checkout.session.completed} de Stripe.
     * <p>
     * Es idempotente: Stripe puede reenviar el mismo evento y el cambio PENDING → PAID es un UPDATE
     * condicional, así que solo la primera llamada publica {@link OrderPaidEvent} (PDF + email).
     * Si el pedido ya se había cancelado por caducidad, se intenta reactivar volviendo a reservar su stock.
     * <p>
     * Un pedido inexistente solo se registra en el log: lanzar un error haría que Stripe reintentase durante días.
     *
     * @param orderId ID del pedido pagado
     */
    @Transactional
    public void confirmPayment(Long orderId) {
        if (orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING, OrderStatus.PAID) == 1) {
            logger.info("✓ Pedido #{} marcado como pagado", orderId);
            eventPublisher.publishEvent(new OrderPaidEvent(orderId));
            return;
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            logger.warn("Pago recibido para el pedido #{}, que no existe", orderId);
        } else if (order.getStatus() == OrderStatus.PAID) {
            logger.info("El pedido #{} ya figuraba como pagado (webhook repetido). Ignorando.", orderId);
        } else {
            reactivateCancelledOrder(order);
        }
    }

    /**
     * Cancela un pedido que sigue PENDING y devuelve su stock. Lo usan el job de caducidad,
     * el webhook {@code checkout.session.expired} y el checkout si falla la creación de la sesión de Stripe.
     * Si el pedido ya no está PENDING (se pagó o ya se canceló) no hace nada.
     *
     * @param orderId ID del pedido
     */
    @Transactional
    public void cancelPendingOrder(Long orderId) {
        if (orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED) == 0) {
            logger.debug("El pedido #{} no está pendiente; no se cancela", orderId);
            return;
        }

        Order order = getOrderEntityById(orderId);
        stockService.releaseOrderStock(order);
        updateItemsStatus(orderId, TicketStatus.CANCELLED);
        logger.info("Pedido #{} cancelado por falta de pago; stock devuelto", orderId);
    }

    // Pago que llega después de cancelar el pedido (p. ej. el webhook se retrasó porque el backend estaba caído)
    private void reactivateCancelledOrder(Order order) {
        Long orderId = order.getId();

        if (!stockService.tryReserveOrderStock(order)) {
            logger.error("Pago recibido del pedido cancelado #{} y ya no hay stock para reactivarlo: " +
                    "requiere reembolso manual en Stripe", orderId);
            return;
        }

        if (orderRepository.updateStatusIfCurrent(orderId, OrderStatus.CANCELLED, OrderStatus.PAID) == 0) {
            // Otro webhook simultáneo lo reactivó antes: se devuelve lo que acabamos de reservar
            stockService.releaseOrderStock(order);
            return;
        }

        updateItemsStatus(orderId, TicketStatus.ACTIVE);
        logger.warn("Pedido #{} reactivado: el pago llegó después de cancelarse por caducidad", orderId);
        eventPublisher.publishEvent(new OrderPaidEvent(orderId));
    }

    private void updateItemsStatus(Long orderId, TicketStatus status) {
        ticketRepository.updateStatusByOrderId(orderId, status);
        campingRepository.updateStatusByOrderId(orderId, status);
    }
}
