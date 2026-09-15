package org.iesalixar.daw2.GarikAsatryan.Valkyria.orders;

import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.DashboardStatsDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.OrderCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.DocumentType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.DashboardService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba las consultas agregadas del dashboard contra MariaDB. La BD la comparten todos los tests,
 * así que se comparan diferencias antes/después en lugar de valores absolutos.
 */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void pendingOrdersDoNotCount_paidOrdersDo() {
        DashboardStatsDTO before = dashboardService.getAdminDashboardStats();

        TicketType type = newTicketType(10, new BigDecimal("25.00"));
        Long orderId = orderService.executeOrder(guestOrder(type, 3), null).getId();

        DashboardStatsDTO pending = dashboardService.getAdminDashboardStats();
        assertThat(pending.getTotalTicketsSold()).isEqualTo(before.getTotalTicketsSold());
        assertThat(pending.getTotalRevenue()).isEqualByComparingTo(before.getTotalRevenue());
        assertThat(breakdownCount(pending, type)).isZero();

        // Sin publicar OrderPaidEvent: el envío del PDF en segundo plano no interesa aquí
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.updateStatusIfCurrent(orderId, OrderStatus.PENDING, OrderStatus.PAID));

        DashboardStatsDTO paid = dashboardService.getAdminDashboardStats();
        assertThat(paid.getTotalTicketsSold()).isEqualTo(before.getTotalTicketsSold() + 3);
        assertThat(paid.getTotalRevenue()).isEqualByComparingTo(before.getTotalRevenue().add(new BigDecimal("75.00")));
        assertThat(breakdownCount(paid, type)).isEqualTo(3);
    }

    @Test
    void cancelledOrdersDoNotCount() {
        TicketType type = newTicketType(10, new BigDecimal("25.00"));
        Long orderId = orderService.executeOrder(guestOrder(type, 2), null).getId();
        orderService.cancelPendingOrder(orderId);

        assertThat(breakdownCount(dashboardService.getAdminDashboardStats(), type)).isZero();
    }

    private long breakdownCount(DashboardStatsDTO stats, TicketType type) {
        return stats.getSalesBreakdown().stream()
                .filter(point -> point.getLabel().equals(type.getName()))
                .mapToLong(DashboardStatsDTO.SalesBreakdownPoint::getCount)
                .sum();
    }

    private TicketType newTicketType(int stock, BigDecimal price) {
        TicketType type = new TicketType();
        type.setName("Dashboard " + UUID.randomUUID().toString().substring(0, 8));
        type.setPrice(price);
        type.setStockTotal(stock);
        type.setStockAvailable(stock);
        return ticketTypeRepository.save(type);
    }

    private OrderCreateDTO guestOrder(TicketType type, int quantity) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setGuestEmail("guest@valkyria.test");
        request.setTickets(IntStream.range(0, quantity)
                .mapToObj(i -> new TicketCreateDTO("Test", "Buyer", DocumentType.DNI, "12345678Z", LocalDate.of(1990, 1, 1), type.getId()))
                .toList());
        return request;
    }
}
