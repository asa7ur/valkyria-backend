package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.CampingMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.OrderMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.TicketMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de pedidos (orders).
 * Orquesta el proceso completo de compra de entradas y reservas de camping.
 * <p>
 * Responsabilidades principales:
 * - Validación de disponibilidad de stock
 * - Cálculo de precios totales
 * - Generación de códigos QR únicos para tickets y campings
 * - Actualización automática de inventario
 * - Soporte para usuarios registrados e invitados
 * - Confirmación de pagos tras notificación de Stripe
 * <p>
 * IMPORTANTE: Los métodos de creación de pedidos son transaccionales para garantizar
 * atomicidad (todo o nada) y consistencia del stock.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final OrderRepository orderRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CampingTypeRepository campingTypeRepository;
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final CampingMapper campingMapper;
    private final PaginationComponent paginationComponent;

    public List<OrderDTO> getAllOrders(FilterDTO filterDTO) {
        logger.info("Recuperando pedidos. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

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
    public List<OrderDTO> getOrdersByUser(String email) {
        logger.info("Recuperando historial de pedidos para usuario: {}", email);

        // Consulta optimizada con ordenación en BD
        List<Order> orders = orderRepository.findByUserEmailOrderByOrderDateDesc(email);

        logger.debug("Total de pedidos encontrados para {}: {}", email, orders.size());

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
                    return new AppException("msg.error.order-not-found", id);
                });
    }

    /**
     * Elimina un pedido por su ID.
     *
     * @param id ID del pedido a eliminar.
     * @throws AppException Si el pedido no existe.
     */
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.error.order-not-found", id));

        logger.info("Restaurando stock para el pedido #{}", id);

        // Devolver stock de todos los tickets del pedido
        for (Ticket ticket : order.getTickets()) {
            TicketType type = ticket.getTicketType();
            if (type != null) {
                type.setStockAvailable(type.getStockAvailable() + 1);
                ticketTypeRepository.save(type);
            }
        }

        // Devolver stock de todos los campings del pedido
        for (Camping camping : order.getCampings()) {
            CampingType type = camping.getCampingType();
            if (type != null) {
                type.setStockAvailable(type.getStockAvailable() + 1);
                campingTypeRepository.save(type);
            }
        }

        orderRepository.delete(order);
        logger.info("✓ Pedido #{} y su stock han sido eliminados/restaurados", id);
    }

    /**
     * PROCESO PRINCIPAL DE COMPRA - Ejecuta y persiste un pedido completo.
     * <p>
     * Este método orquesta todo el flujo de creación de pedido:
     * 1. Valida disponibilidad de stock para cada item
     * 2. Genera códigos QR únicos para tickets y campings
     * 3. Crea las entidades relacionadas (Ticket, Camping)
     * 4. Calcula el precio total
     * 5. Actualiza el inventario (descuenta stock)
     * 6. Persiste el pedido con estado PENDING
     * <p>
     * TRANSACCIONAL: Si cualquier paso falla, se revierte toda la operación (rollback).
     * Esto garantiza que nunca se descuente stock sin crear el pedido, o viceversa.
     * <p>
     * Soporta dos tipos de compra:
     * - Usuario registrado: user != null
     * - Compra como invitado: user == null, requiere guestEmail
     *
     * @param request DTO con los items a comprar (tickets y/o campings)
     * @param user    Usuario registrado (puede ser null para compras de invitado)
     * @return Pedido creado con estado PENDING (pendiente de pago)
     * @throws AppException si hay problemas de stock, tipos no encontrados, etc.
     */
    @Transactional
    public Order executeOrder(OrderCreateDTO request, User user) {
        // Log diferenciado según tipo de compra
        if (user != null) {
            logger.info("Iniciando procesamiento de pedido para usuario registrado: {}",
                    user.getEmail());
        } else {
            logger.info("Iniciando procesamiento de pedido para invitado: {}",
                    request.getGuestEmail());
        }

        // Paso 1: Crear la entidad Order con datos iniciales
        Order order = new Order();
        order.setUser(user); // Puede ser null (invitado)
        order.setGuestEmail(request.getGuestEmail()); // Solo se usa si user == null
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING); // Pendiente hasta confirmación de pago

        logger.debug("Orden inicializada. Estado: {}, Fecha: {}",
                order.getStatus(), order.getOrderDate());

        // Variable para acumular el precio total
        BigDecimal totalPrice = BigDecimal.ZERO;

        // ========== SECCIÓN 1: PROCESAMIENTO DE TICKETS (ENTRADAS) ==========

        if (request.getTickets() != null && !request.getTickets().isEmpty()) {
            logger.info("Procesando {} tickets para el pedido", request.getTickets().size());

            int ticketIndex = 0;
            for (TicketCreateDTO tDto : request.getTickets()) {
                ticketIndex++;
                logger.debug("Procesando ticket {}/{}: Tipo ID {}",
                        ticketIndex, request.getTickets().size(), tDto.getTicketTypeId());

                // 1.1: Buscar el tipo de ticket
                TicketType type = ticketTypeRepository.findById(tDto.getTicketTypeId())
                        .orElseThrow(() -> {
                            logger.error("Tipo de ticket con ID {} no encontrado",
                                    tDto.getTicketTypeId());
                            return new AppException("msg.error.ticket-type-not-found");
                        });

                logger.debug("Tipo de ticket encontrado: {} (Stock: {}, Precio: {})",
                        type.getName(), type.getStockAvailable(), type.getPrice());

                // 1.2: Validar disponibilidad de stock
                if (type.getStockAvailable() <= 0) {
                    logger.error("Sin stock disponible para ticket tipo: {}", type.getName());
                    throw new AppException("msg.error.no-stock", type.getName());
                }

                // 1.3: Generar código QR único para este ticket
                // Formato: TKT-XXXXXXXX (8 caracteres aleatorios en mayúsculas)
                String qr = "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                logger.debug("QR generado para ticket: {}", qr);

                // 1.4: Crear entidad Ticket usando el mapper
                Ticket ticket = ticketMapper.toEntityFromOrder(tDto, type, order, qr);

                // 1.5: Agregar ticket a la colección del pedido
                order.getTickets().add(ticket);

                // 1.6: Acumular precio al total
                totalPrice = totalPrice.add(type.getPrice());
                logger.debug("Precio acumulado después de ticket {}: {} €", ticketIndex, totalPrice);

                // 1.7: Decrementar stock y persistir
                int newStock = type.getStockAvailable() - 1;
                type.setStockAvailable(newStock);
                ticketTypeRepository.save(type);
                logger.info("Stock actualizado para '{}': {} -> {}",
                        type.getName(), newStock + 1, newStock);
            }

            logger.info("Todos los tickets procesados exitosamente. Total tickets: {}",
                    request.getTickets().size());
        } else {
            logger.debug("No hay tickets en este pedido");
        }

        // ========== SECCIÓN 2: PROCESAMIENTO DE CAMPINGS (RESERVAS) ==========

        if (request.getCampings() != null && !request.getCampings().isEmpty()) {
            logger.info("Procesando {} campings para el pedido", request.getCampings().size());

            int campingIndex = 0;
            for (CampingCreateDTO cDto : request.getCampings()) {
                campingIndex++;
                logger.debug("Procesando camping {}/{}: Tipo ID {}",
                        campingIndex, request.getCampings().size(), cDto.getCampingTypeId());

                // 2.1: Buscar el tipo de camping
                CampingType type = campingTypeRepository.findById(cDto.getCampingTypeId())
                        .orElseThrow(() -> {
                            logger.error("Tipo de camping con ID {} no encontrado",
                                    cDto.getCampingTypeId());
                            return new AppException("msg.error.camping-type-not-found");
                        });

                logger.debug("Tipo de camping encontrado: {} (Stock: {}, Precio: {})",
                        type.getName(), type.getStockAvailable(), type.getPrice());

                // 2.2: Validar disponibilidad de stock
                if (type.getStockAvailable() <= 0) {
                    logger.error("Sin stock disponible para camping tipo: {}", type.getName());
                    throw new AppException("msg.error.no-stock", type.getName());
                }

                // 2.3: Generar código QR único para este camping
                // Formato: CMP-XXXXXXXX (8 caracteres aleatorios en mayúsculas)
                String qr = "CMP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                logger.debug("QR generado para camping: {}", qr);

                // 2.4: Crear entidad Camping usando el mapper
                Camping camping = campingMapper.toEntityFromOrder(cDto, type, order, qr);

                // 2.5: Agregar camping a la colección del pedido
                order.getCampings().add(camping);

                // 2.6: Acumular precio al total
                totalPrice = totalPrice.add(type.getPrice());
                logger.debug("Precio acumulado después de camping {}: {} €",
                        campingIndex, totalPrice);

                // 2.7: Decrementar stock y persistir
                int newStock = type.getStockAvailable() - 1;
                type.setStockAvailable(newStock);
                campingTypeRepository.save(type);
                logger.info("Stock actualizado para '{}': {} -> {}",
                        type.getName(), newStock + 1, newStock);
            }

            logger.info("Todos los campings procesados exitosamente. Total campings: {}",
                    request.getCampings().size());
        } else {
            logger.debug("No hay campings en este pedido");
        }

        // ========== FINALIZACIÓN DEL PEDIDO ==========

        // Establecer el precio total calculado
        order.setTotalPrice(totalPrice);
        logger.debug("Precio total del pedido: {} €", totalPrice);

        // Persistir el pedido completo (cascada guardará tickets y campings)
        Order savedOrder = orderRepository.save(order);

        logger.info("✓ Pedido #{} creado exitosamente. Total: {} €, Items: {} tickets + {} campings",
                savedOrder.getId(),
                savedOrder.getTotalPrice(),
                savedOrder.getTickets().size(),
                savedOrder.getCampings().size());

        return savedOrder;
    }

    /**
     * Confirma el pago de un pedido tras recibir notificación exitosa de Stripe.
     * Actualiza el estado del pedido de PENDING a PAID.
     * <p>
     * Este método es invocado típicamente por un webhook de Stripe o tras
     * redirección exitosa del proceso de pago.
     * <p>
     * TRANSACCIONAL: Garantiza atomicidad de la actualización del estado.
     *
     * @param orderId ID del pedido a confirmar
     * @return Pedido actualizado con estado PAID
     * @throws AppException si el pedido no existe
     */
    @Transactional
    public Order confirmPayment(Long orderId) {
        logger.info("Iniciando confirmación de pago para pedido #{}", orderId);

        // Buscar el pedido
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    logger.error("Pedido con ID {} no encontrado para confirmación de pago", orderId);
                    return new AppException("msg.error.order-not-found", orderId);
                });

        // Log del estado anterior
        OrderStatus previousStatus = order.getStatus();
        logger.debug("Estado actual del pedido #{}: {}", orderId, previousStatus);

        if (order.getStatus() == OrderStatus.PAID) {
            logger.info("El pedido #{} ya figuraba como pagado. Ignorando.", orderId);
            return order;
        }

        // Actualizar estado a PAID
        order.setStatus(OrderStatus.PAID);

        // Persistir cambio
        Order updatedOrder = orderRepository.save(order);

        logger.info("✓ Pedido #{} confirmado. Estado: {} -> PAID. Total: {} €",
                orderId, previousStatus, updatedOrder.getTotalPrice());

        return updatedOrder;
    }
}