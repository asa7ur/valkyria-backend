package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Camping;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Ticket;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Reserva y liberación de stock con UPDATE condicionales en BD, seguras ante compras simultáneas.
 * Nunca se modifica el stock a través de la entidad (leer, restar y guardar provoca sobreventa).
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    private final TicketTypeRepository ticketTypeRepository;
    private final CampingTypeRepository campingTypeRepository;

    @Transactional
    public void reserveTickets(TicketType type, int quantity) {
        if (ticketTypeRepository.reserveStock(type.getId(), quantity) == 0) {
            logger.warn("Stock insuficiente para el tipo de entrada '{}' (solicitadas {})", type.getName(), quantity);
            throw AppException.conflict("msg.error.no-stock", type.getName());
        }
    }

    @Transactional
    public void reserveCampings(CampingType type, int quantity) {
        if (campingTypeRepository.reserveStock(type.getId(), quantity) == 0) {
            logger.warn("Stock insuficiente para el tipo de camping '{}' (solicitadas {})", type.getName(), quantity);
            throw AppException.conflict("msg.error.no-stock", type.getName());
        }
    }

    @Transactional
    public void releaseTickets(TicketType type, int quantity) {
        if (ticketTypeRepository.releaseStock(type.getId(), quantity) == 0) {
            logger.warn("No se devuelven {} entradas de '{}': se superaría el stock total", quantity, type.getName());
        }
    }

    @Transactional
    public void releaseCampings(CampingType type, int quantity) {
        if (campingTypeRepository.releaseStock(type.getId(), quantity) == 0) {
            logger.warn("No se devuelven {} campings de '{}': se superaría el stock total", quantity, type.getName());
        }
    }

    @Transactional
    public void releaseOrderStock(Order order) {
        countTicketsByType(order).forEach((type, count) -> releaseTickets(type, count.intValue()));
        countCampingsByType(order).forEach((type, count) -> releaseCampings(type, count.intValue()));
    }

    /**
     * Vuelve a reservar el stock de un pedido (un pago que llega después de cancelarse el pedido).
     * No lanza excepciones, para no marcar la transacción para rollback: si falta stock de algún tipo
     * devuelve lo ya reservado y retorna false.
     */
    @Transactional
    public boolean tryReserveOrderStock(Order order) {
        Map<TicketType, Long> reservedTickets = new HashMap<>();
        Map<CampingType, Long> reservedCampings = new HashMap<>();

        for (Map.Entry<TicketType, Long> entry : countTicketsByType(order).entrySet()) {
            if (ticketTypeRepository.reserveStock(entry.getKey().getId(), entry.getValue().intValue()) == 0) {
                reservedTickets.forEach((type, count) -> releaseTickets(type, count.intValue()));
                return false;
            }
            reservedTickets.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<CampingType, Long> entry : countCampingsByType(order).entrySet()) {
            if (campingTypeRepository.reserveStock(entry.getKey().getId(), entry.getValue().intValue()) == 0) {
                reservedTickets.forEach((type, count) -> releaseTickets(type, count.intValue()));
                reservedCampings.forEach((type, count) -> releaseCampings(type, count.intValue()));
                return false;
            }
            reservedCampings.put(entry.getKey(), entry.getValue());
        }
        return true;
    }

    private Map<TicketType, Long> countTicketsByType(Order order) {
        return order.getTickets().stream()
                .map(Ticket::getTicketType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));
    }

    private Map<CampingType, Long> countCampingsByType(Order order) {
        return order.getCampings().stream()
                .map(Camping::getCampingType)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(type -> type, Collectors.counting()));
    }
}
