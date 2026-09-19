package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.DashboardStatsDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // stubs comunes en @BeforeEach que algunos tests sobrescriben
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private TicketRepository ticketRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private CampingRepository campingRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void stubDefaults() {
        when(orderRepository.sumTotalPriceByStatus(OrderStatus.PAID)).thenReturn(BigDecimal.ZERO);
        when(ticketTypeRepository.sumStockTotal()).thenReturn(2000L);
        when(orderRepository.findDailyRevenue()).thenReturn(List.of());
        when(ticketRepository.countByType()).thenReturn(List.of());
        when(campingRepository.countByType()).thenReturn(List.of());
    }

    // ─── totalRevenue ─────────────────────────────────────────────────────────

    @Test
    void getStats_revenueIsSumOfPaidOrdersFromDatabase() {
        when(orderRepository.sumTotalPriceByStatus(OrderStatus.PAID)).thenReturn(new BigDecimal("300.00"));

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalRevenue()).isEqualByComparingTo(new BigDecimal("300.00"));
        verify(orderRepository, never()).findAll();
    }

    // ─── ticketCapacityPercentage ─────────────────────────────────────────────

    @Test
    void getStats_capacityIsSoldTicketsOverTotalStock() {
        when(ticketRepository.countSold()).thenReturn(1000L);

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(50.0); // 1000/2000*100
    }

    @Test
    void getStats_capacityUsesConfiguredStockNotAFixedNumber() {
        when(ticketRepository.countSold()).thenReturn(150L);
        when(ticketTypeRepository.sumStockTotal()).thenReturn(600L);

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(25.0);
    }

    @Test
    void getStats_ticketCapacityCappedAt100Percent() {
        when(ticketRepository.countSold()).thenReturn(5000L);

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(100.0);
    }

    @Test
    void getStats_noTicketTypes_capacityIsZero() {
        when(ticketRepository.countSold()).thenReturn(10L);
        when(ticketTypeRepository.sumStockTotal()).thenReturn(0L);

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTicketCapacityPercentage()).isEqualTo(0.0);
    }

    // ─── aggregate counters ───────────────────────────────────────────────────

    @Test
    void getStats_returnsCountsFromRepositories() {
        when(artistRepository.count()).thenReturn(12L);
        when(ticketRepository.countSold()).thenReturn(500L);
        when(userRepository.count()).thenReturn(350L);

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getTotalArtists()).isEqualTo(12L);
        assertThat(stats.getTotalTicketsSold()).isEqualTo(500L);
        assertThat(stats.getTotalActiveUsers()).isEqualTo(350L);
    }

    // ─── salesTrend ───────────────────────────────────────────────────────────

    @Test
    void getStats_mapsSalesTrendPointsFromRepository() {
        Object[] row = {"2025-07-10", new BigDecimal("1500.00")};
        when(orderRepository.findDailyRevenue()).thenReturn(List.<Object[]>of(row));

        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesTrend()).hasSize(1);
        assertThat(stats.getSalesTrend().getFirst().getDate()).isEqualTo("2025-07-10");
        assertThat(stats.getSalesTrend().getFirst().getAmount())
                .isEqualByComparingTo(new BigDecimal("1500.00"));
    }

    @Test
    void getStats_emptySalesTrend_returnsEmptyList() {
        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesTrend()).isEmpty();
    }

    // ─── salesBreakdown ───────────────────────────────────────────────────────

    @Test
    void getStats_combinesTicketAndCampingBreakdown() {
        Object[] ticketRow = {"General", 100L};
        Object[] campingRow = {"Estándar", 50L};
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
        DashboardStatsDTO stats = dashboardService.getAdminDashboardStats();

        assertThat(stats.getSalesBreakdown()).isEmpty();
    }
}
