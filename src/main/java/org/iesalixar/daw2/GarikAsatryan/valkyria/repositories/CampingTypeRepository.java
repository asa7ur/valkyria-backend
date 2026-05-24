package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampingTypeRepository extends JpaRepository<CampingType, Long> {
    @Query("SELECT c FROM CampingType c WHERE " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) ")
    Page<CampingType> searchCampingTypes(@Param("searchTerm") String searchTerm, Pageable pageable);
}
