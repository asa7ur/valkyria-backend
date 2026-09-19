package org.iesalixar.daw2.GarikAsatryan.valkyria.components;

import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationComponentTest {

    private static final Set<String> SORTABLE = Set.of("id", "name");

    private final PaginationComponent paginationComponent = new PaginationComponent();

    @Test
    void createPageable_withoutOrder_usesDefaultSortAscending() {
        Pageable pageable = paginationComponent.createPageable(filter(0, 10, null, null), "id", SORTABLE);

        assertThat(pageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void createPageable_allowedField_sortsDescending() {
        Pageable pageable = paginationComponent.createPageable(filter(2, 10, "name", "desc"), "id", SORTABLE);

        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getPageNumber()).isEqualTo(2);
    }

    @Test
    void createPageable_fieldNotAllowed_throwsBadRequest() {
        assertThatThrownBy(() -> paginationComponent.createPageable(filter(0, 10, "password", null), "id", SORTABLE))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.invalid-sort")
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPageable_hugePageSize_isCapped() {
        Pageable pageable = paginationComponent.createPageable(filter(0, 1_000_000, null, null), "id", SORTABLE);

        assertThat(pageable.getPageSize()).isEqualTo(PaginationComponent.MAX_PAGE_SIZE);
    }

    @Test
    void createPageable_invalidPageAndSize_useDefaults() {
        Pageable pageable = paginationComponent.createPageable(filter(-3, 0, null, null), "id", SORTABLE);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(9);
    }

    private FilterDTO filter(int page, int size, String order, String orderBy) {
        FilterDTO filter = new FilterDTO();
        filter.setPage(page);
        filter.setItemsPerPage(size);
        filter.setOrder(order);
        filter.setOrderBy(orderBy);
        return filter;
    }
}
