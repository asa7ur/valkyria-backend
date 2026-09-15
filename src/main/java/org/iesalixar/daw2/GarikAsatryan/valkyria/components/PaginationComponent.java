package org.iesalixar.daw2.GarikAsatryan.valkyria.components;

import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PaginationComponent {

    static final int DEFAULT_PAGE_SIZE = 9;
    // Los desplegables del panel admin piden hasta 1000 elementos de una vez
    public static final int MAX_PAGE_SIZE = 1000;

    /**
     * Convierte los parámetros de FilterDTO en un objeto Pageable.
     *
     * @param sortableFields campos por los que se permite ordenar. Sin esta lista se podría ordenar por
     *                       cualquier atributo (incluida la contraseña) o provocar un 500 con uno inexistente.
     * @throws AppException 400 si el campo de ordenación no está permitido
     */
    public Pageable createPageable(FilterDTO filterDTO, String defaultOrder, Set<String> sortableFields) {
        String sortProperty = (filterDTO.getOrder() == null || filterDTO.getOrder().isBlank())
                ? defaultOrder : filterDTO.getOrder();

        if (!sortableFields.contains(sortProperty)) {
            throw AppException.badRequest("msg.error.invalid-sort", sortProperty);
        }

        Sort sort = "desc".equalsIgnoreCase(filterDTO.getOrderBy())
                ? Sort.by(sortProperty).descending()
                : Sort.by(sortProperty).ascending();

        int page = Math.max(filterDTO.getPage(), 0);
        int size = (filterDTO.getItemsPerPage() < 1)
                ? DEFAULT_PAGE_SIZE
                : Math.min(filterDTO.getItemsPerPage(), MAX_PAGE_SIZE);

        return PageRequest.of(page, size, sort);
    }

    /**
     * Actualiza los metadatos de FilterDTO basándose en el resultado de la página.
     */
    public void updateFilterMetadata(FilterDTO filterDTO, Page<?> page) {
        filterDTO.setTotalPages(page.getTotalPages());
        filterDTO.setTotalElements((int) page.getTotalElements());
    }
}
