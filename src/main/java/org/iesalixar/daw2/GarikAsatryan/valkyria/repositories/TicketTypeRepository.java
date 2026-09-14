package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    @Query("SELECT t FROM TicketType t WHERE " +
            "LOWER(t.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ")
    Page<TicketType> searchTicketTypes(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Operaciones atómicas en BD: devuelven 0 si no hay stock suficiente (o se superaría el total)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE TicketType t SET t.stockAvailable = t.stockAvailable - :qty " +
            "WHERE t.id = :id AND t.stockAvailable >= :qty")
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE TicketType t SET t.stockAvailable = t.stockAvailable + :qty " +
            "WHERE t.id = :id AND t.stockAvailable + :qty <= t.stockTotal")
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);
}
