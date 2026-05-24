package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Ticket;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.TicketMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de TICKETS.
 * Proporciona operaciones CRUD completas con validación de tipos de tickets.
 */
@Service
@RequiredArgsConstructor
public class TicketService {
    private static final Logger logger = LoggerFactory.getLogger(TicketService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final TicketMapper ticketMapper;
    private final PaginationComponent paginationComponent;

    /**
     * Obtiene una lista de tickets basada en filtros.
     * Actualiza el FilterDTO con los metadatos de paginación.
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getAllTickets(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de entradas. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<Ticket> ticketPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? ticketRepository.searchTickets(filterDTO.getSearch(), pageable)
                : ticketRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, ticketPage);

        logger.debug("Entradas encontradas: {} de {} totales",
                ticketPage.getNumberOfElements(),
                ticketPage.getTotalElements());

        return ticketPage.getContent().stream()
                .map(ticketMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el detalle completo de un ticket por su ID.
     * Incluye información del asistente, tipo de entrada y código QR.
     */
    @Transactional(readOnly = true)
    public TicketDTO getTicketById(Long id) {
        return ticketRepository.findById(id)
                .map(ticketMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.ticket.not-found", id));
    }

    /**
     * Crea un nuevo ticket manualmente asociándolo a un TicketType.
     * NOTA: En flujo normal de compra, los tickets se crean automáticamente
     * en OrderService.executeOrder() con generación de QR y asignación a pedido.
     *
     * @param dto DTO con los datos del ticket a crear
     * @return DTO del ticket creado
     * @throws AppException si el tipo de ticket no existe
     */
    @Transactional
    public TicketDTO createTicket(TicketCreateDTO dto) {
        logger.info("Iniciando creación manual de entrada para: {} {}",
                dto.getFirstName(), dto.getLastName());
        logger.debug("Tipo de entrada solicitado ID: {}", dto.getTicketTypeId());

        TicketType type = ticketTypeRepository.findById(dto.getTicketTypeId())
                .orElseThrow(() -> {
                    logger.error("Tipo de entrada con ID {} no encontrado al crear ticket",
                            dto.getTicketTypeId());
                    return new AppException("msg.ticket.type-not-found", dto.getTicketTypeId());
                });

        logger.debug("Tipo de entrada encontrado: {} (Precio: {}, Stock: {})",
                type.getName(), type.getPrice(), type.getStockAvailable());

        Ticket ticket = ticketMapper.toEntity(dto);
        ticket.setTicketType(type);
        logger.debug("Relación con tipo de entrada establecida");

        Ticket saved = ticketRepository.save(ticket);

        logger.info("✓ Entrada creada exitosamente. ID: {}, Asistente: {} {}, Tipo: {}",
                saved.getId(),
                saved.getFirstName(),
                saved.getLastName(),
                type.getName());

        return ticketMapper.toDTO(saved);
    }

    /**
     * Actualiza un ticket existente.
     * Permite cambiar el asistente o el tipo de entrada.
     * <p>
     * CASOS DE USO:
     * - Corrección de nombres mal escritos
     * - Cambio de tipo de entrada (upgrade/downgrade)
     * - Transferencia de entrada a otra persona
     * <p>
     * IMPORTANTE: No se puede cambiar el pedido asociado ni el código QR.
     *
     * @param id  ID del ticket a actualizar
     * @param dto DTO con los nuevos datos
     * @return DTO del ticket actualizado
     * @throws AppException si el ticket o el tipo de entrada no existen
     */
    @Transactional
    public TicketDTO updateTicket(Long id, TicketCreateDTO dto) {
        logger.info("Iniciando actualización de entrada con ID: {}", id);

        // Paso 1: Buscar el ticket existente
        Ticket existing = ticketRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Entrada con ID {} no encontrada para actualización", id);
                    return new AppException("msg.ticket.not-found", id);
                });

        logger.debug("Entrada encontrada. Datos actuales: Asistente={} {}, Tipo={}",
                existing.getFirstName(),
                existing.getLastName(),
                existing.getTicketType().getName());

        // Paso 2: Buscar y validar el nuevo tipo de entrada
        TicketType type = ticketTypeRepository.findById(dto.getTicketTypeId())
                .orElseThrow(() -> {
                    logger.error("Tipo de entrada con ID {} no encontrado al actualizar ticket",
                            dto.getTicketTypeId());
                    return new AppException("msg.ticket.type-not-found", dto.getTicketTypeId());
                });

        logger.debug("Nuevo tipo de entrada encontrado: {}", type.getName());

        // Paso 3: Actualizar los campos de la entidad
        ticketMapper.updateEntityFromDTO(dto, existing);
        existing.setTicketType(type);
        logger.debug("Datos de la entrada actualizados en memoria");

        // Paso 4: Persistir cambios
        Ticket updated = ticketRepository.save(existing);

        logger.info("✓ Entrada ID {} actualizada correctamente. Nuevo asistente: {} {}, Nuevo tipo: {}",
                id,
                updated.getFirstName(),
                updated.getLastName(),
                type.getName());

        return ticketMapper.toDTO(updated);
    }

    /**
     * Elimina un ticket del sistema.
     * <p>
     * ADVERTENCIA: Esta operación debe usarse con precaución.
     * Eliminar un ticket puede:
     * - Dejar inconsistente la información del pedido asociado
     * - Afectar los reportes de ventas
     * - Causar problemas si el QR ya fue enviado al usuario
     * <p>
     * CASOS DE USO VÁLIDOS:
     * - Cancelación de entrada antes de enviar confirmación
     * - Corrección de entradas duplicadas por error
     * - Tickets de prueba que deben eliminarse
     *
     * @param id ID del ticket a eliminar
     * @throws AppException si el ticket no existe
     */
    @Transactional
    public void deleteTicket(Long id) {
        logger.info("Iniciando eliminación de entrada con ID: {}", id);

        // 1. Buscar el ticket para obtener su tipo antes de borrar
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.ticket.not-found", id));

        // 2. Recuperar el tipo y devolver el stock
        TicketType type = ticket.getTicketType();
        if (type != null) {
            type.setStockAvailable(type.getStockAvailable() + 1);
            ticketTypeRepository.save(type);
            logger.info("Stock devuelto para ticket tipo '{}'. Nuevo stock: {}",
                    type.getName(), type.getStockAvailable());
        }

        ticketRepository.delete(ticket);
        logger.info("✓ Entrada con ID {} eliminada", id);
    }
}