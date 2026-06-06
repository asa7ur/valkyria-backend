package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.DashboardStatsDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Order;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private CampingRepository campingRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private Order order(OrderStatus status, BigDecimal price) {
        Order o = new Order();
        o.setStatus(status);
        o.setTotalPrice(price);
        return o;
    }

    private void stubEmptyTrends() {
        when(orderRepository.findDailyRevenue()).thenReturn(List.of());
        when(ticketRepository.countByType()).thenReturn(List.of());
        when(campingRepository.countByType()).thenReturn(List.of());
    }

    // ─── totalRevenue ─────────────────────────────────────────────────────────

    @Test
    void getStats_sumsPaidOrdersOnly() {
        when(orderRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PAID, new BigDecimal("100.00")),
                order(OrderStatus.PAID, new BigDecimal("200.00")),
                order(OrderStatus.PENDING, new BigDecimal("300.00")), // must be excluded
                order(OrderStatus.CANCELLED, new BigDecimal("50.00"))  // must be excluded
        ));
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void getStats_noOrders_revenueIsZero() {
        when(orderRepository.findAll()).thenReturn(List.of());
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getStats_paidOrderWithNullPrice_isSkippedSafely() {
        Order nullPriceOrder = order(OrderStatus.PAID, null);
        when(orderRepository.findAll()).thenReturn(List.of(
                nullPriceOrder,
                order(OrderStatus.PAID, new BigDecimal("75.00"))
        ));
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        // null prices are filtered out — only the valid one is summed
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void getStats_allOrdersPending_revenueIsZero() {
        when(orderRepository.findAll()).thenReturn(List.of(
                order(OrderStatus.PENDING, new BigDecimal("500.00")),
                order(OrderStatus.PENDING, new BigDecimal("200.00"))
        ));
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── ticketCapacityPercentage ─────────────────────────────────────────────

    @Test
    void getStats_ticketCapacityCalculatedFrom2000Base() {
        when(orderRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.count()).thenReturn(1000L);
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(50.0); // 1000/2000*100
    }

    @Test
    void getStats_ticketCapacityCappedAt100Percent() {
        when(orderRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.count()).thenReturn(5000L); // exceeds max capacity
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(100.0);
    }

    @Test
    void getStats_zeroTickets_capacityIsZero() {
        when(orderRepository.findAll()).thenReturn(List.of());
        when(ticketRepository.count()).thenReturn(0L);
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(0.0);
    }

    // ─── aggregate counters ───────────────────────────────────────────────────

    @Test
    void getStats_returnsCountsFromRepositories() {
        when(orderRepository.findAll()).thenReturn(List.of());
        when(artistRepository.count()).thenReturn(12L);
        when(ticketRepository.count()).thenReturn(500L);
        when(userRepository.count()).thenReturn(350L);
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalArtists()).isEqualTo(12L);
        assertThat(stats.getTotalTicketsSold()).isEqualTo(500L);
        assertThat(stats.getTotalActiveUsers()).isEqualTo(350L);
    }

    // ─── salesTrend ───────────────────────────────────────────────────────────

    @Test
    void getStats_mapsSalesTrendPointsFromRepository() {
        Object[] row = {"2025-07-10", new BigDecimal("1500.00")};
        when(orderRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findDailyRevenue()).thenReturn(List.<Object[]>of(row));
        when(ticketRepository.countByType()).thenReturn(List.of());
        when(campingRepository.countByType()).thenReturn(List.of());

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesTrend()).hasSize(1);
        assertThat(stats.getSalesTrend().getFirst().getDate()).isEqualTo("2025-07-10");
        assertThat(stats.getSalesTrend().getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void getStats_emptySalesTrend_returnsEmptyList() {
        when(orderRepository.findAll()).thenReturn(List.of());
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesTrend()).isEmpty();
    }

    // ─── salesBreakdown ───────────────────────────────────────────────────────

    @Test
    void getStats_combinesTicketAndCampingBreakdown() {
        Object[] ticketRow = {"General", 100L};
        Object[] campingRow = {"Estándar", 50L};

        when(orderRepository.findAll()).thenReturn(List.of());
        when(orderRepository.findDailyRevenue()).thenReturn(List.of());
        when(ticketRepository.countByType()).thenReturn(List.<Object[]>of(ticketRow));
        when(campingRepository.countByType()).thenReturn(List.<Object[]>of(campingRow));

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesBreakdown()).hasSize(2);
        assertThat(stats.getSalesBreakdown().get(0).getLabel()).isEqualTo("General");
        assertThat(stats.getSalesBreakdown().get(0).getCount()).isEqualTo(100L);
        assertThat(stats.getSalesBreakdown().get(1).getLabel()).isEqualTo("Estándar");
        assertThat(stats.getSalesBreakdown().get(1).getCount()).isEqualTo(50L);
    }

    @Test
    void getStats_noSalesBreakdownData_returnsEmptyList() {
        when(orderRepository.findAll()).thenReturn(List.of());
        stubEmptyTrends();

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesBreakdown()).isEmpty();
    }
}
