package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampingTypeRepository extends JpaRepository<CampingType, Long> {
    @Query("SELECT c FROM CampingType c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ")
    Page<CampingType> searchCampingTypes(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Operaciones atómicas en BD: devuelven 0 si no hay stock suficiente (o se superaría el total)
    @Modifying(flushAutomatically = true)
    @Query("UPDATE CampingType c SET c.stockAvailable = c.stockAvailable - :qty " +
            "WHERE c.id = :id AND c.stockAvailable >= :qty")
    int reserveStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE CampingType c SET c.stockAvailable = c.stockAvailable + :qty " +
            "WHERE c.id = :id AND c.stockAvailable + :qty <= c.stockTotal")
    int releaseStock(@Param("id") Long id, @Param("qty") int qty);
}
