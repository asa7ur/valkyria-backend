package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Ticket;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    @Query("SELECT t FROM Ticket t WHERE " +
            "LOWER(t.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.documentType) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.ticketType.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(t.status) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Ticket> searchTickets(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT t.ticketType.name, COUNT(t) FROM Ticket t WHERE t.status <> 'CANCELLED' GROUP BY t.ticketType.name ORDER BY COUNT(t) DESC")
    List<Object[]> countByType();

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Ticket t SET t.status = :status WHERE t.order.id = :orderId")
    int updateStatusByOrderId(@Param("orderId") Long orderId, @Param("status") TicketStatus status);
}
