package org.iesalixar.daw2.GarikAsatryan.valkyria.repositories;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // LEFT JOIN explícito: con o.user.* el join implícito es INNER y excluye los pedidos de invitado
    @Query("SELECT o FROM Order o LEFT JOIN o.user u WHERE " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.guestEmail) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(o.status) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    Page<Order> searchOrders(@Param("searchTerm") String searchTerm, Pageable pageable);

    // Cambio de estado condicional: con webhooks duplicados o simultáneos solo uno de ellos lo aplica
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Order o SET o.status = :newStatus WHERE o.id = :id AND o.status = :expectedStatus")
    int updateStatusIfCurrent(@Param("id") Long id,
                              @Param("expectedStatus") OrderStatus expectedStatus,
                              @Param("newStatus") OrderStatus newStatus);

    // Se compara con NOW() de la BD porque order_date lo pone la BD, cuya zona horaria puede no ser la de la JVM
    @Query(value = "SELECT id FROM orders WHERE status = 'PENDING' AND order_date < NOW() - INTERVAL :minutes MINUTE",
            nativeQuery = true)
    List<Long> findPendingOrderIdsOlderThan(@Param("minutes") int minutes);

    @Query("SELECT o FROM Order o JOIN FETCH o.user WHERE o.user.email = :email ORDER BY o.orderDate DESC")
    List<Order> findByUserEmailOrderByOrderDateDesc(@Param("email") String email);

    @Query("SELECT CAST(o.orderDate AS date), SUM(o.totalPrice) " +
           "FROM Order o WHERE o.status = 'PAID' " +
           "GROUP BY CAST(o.orderDate AS date) " +
           "ORDER BY CAST(o.orderDate AS date) ASC")
    List<Object[]> findDailyRevenue();
}
