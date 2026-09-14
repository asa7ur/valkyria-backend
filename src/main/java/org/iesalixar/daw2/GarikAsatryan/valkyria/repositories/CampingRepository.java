package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Camping;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampingRepository extends JpaRepository<Camping, Long> {
    @Query("SELECT c FROM Camping c WHERE " +
            "LOWER(c.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.documentType) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.documentNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.campingType.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.status) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Camping> searchCampings(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT c.campingType.name, COUNT(c) FROM Camping c WHERE c.status <> 'CANCELLED' GROUP BY c.campingType.name ORDER BY COUNT(c) DESC")
    List<Object[]> countByType();

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Camping c SET c.status = :status WHERE c.order.id = :orderId")
    int updateStatusByOrderId(@Param("orderId") Long orderId, @Param("status") TicketStatus status);
}
