package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o WHERE " +
            "LOWER(o.user.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.user.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.user.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.status) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Order> searchOrders(@Param("searchTerm") String searchTerm, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.user.email = :email ORDER BY o.orderDate DESC")
    List<Order> findByUserEmailOrderByOrderDateDesc(@Param("email") String email);

    @Query("SELECT CAST(o.orderDate AS date), SUM(o.totalPrice) " +
           "FROM Order o WHERE o.status = 'PAID' " +
           "GROUP BY CAST(o.orderDate AS date) " +
           "ORDER BY CAST(o.orderDate AS date) ASC")
    List<Object[]> findDailyRevenue();
}
