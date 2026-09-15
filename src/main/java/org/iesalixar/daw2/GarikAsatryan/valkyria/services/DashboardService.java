package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.DashboardStatsDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final ArtistRepository artistRepository;
    private final TicketRepository ticketRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CampingRepository campingRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getAdminDashboardStats() {
        // Ingresos de pedidos pagados, sumados en BD (antes se cargaban todos los pedidos en memoria)
        BigDecimal totalRevenue = orderRepository.sumTotalPriceByStatus(OrderStatus.PAID);

        long artistsCount = artistRepository.count();
        long usersCount = userRepository.count();

        // Entradas vendidas: sin las canceladas ni las de pedidos pendientes de pago
        long ticketsSold = ticketRepository.countSold();

        // Capacidad = vendidas / entradas totales puestas a la venta en todos los tipos
        long ticketCapacity = ticketTypeRepository.sumStockTotal();
        double capacity = ticketCapacity > 0 ? (ticketsSold * 100.0) / ticketCapacity : 0.0;

        List<DashboardStatsDTO.RevenuePoint> salesTrend = orderRepository.findDailyRevenue().stream()
                .map(row -> new DashboardStatsDTO.RevenuePoint(
                        row[0].toString(),
                        (BigDecimal) row[1]
                ))
                .collect(Collectors.toList());

        List<DashboardStatsDTO.SalesBreakdownPoint> salesBreakdown = new ArrayList<>();
        ticketRepository.countByType().stream()
                .map(row -> new DashboardStatsDTO.SalesBreakdownPoint(row[0].toString(), (Long) row[1]))
                .forEach(salesBreakdown::add);
        campingRepository.countByType().stream()
                .map(row -> new DashboardStatsDTO.SalesBreakdownPoint(row[0].toString(), (Long) row[1]))
                .forEach(salesBreakdown::add);

        return DashboardStatsDTO.builder()
                .totalRevenue(totalRevenue)
                .totalArtists(artistsCount)
                .totalTicketsSold(ticketsSold)
                .totalActiveUsers(usersCount)
                .ticketCapacityPercentage(Math.min(capacity, 100.0))
                .salesTrend(salesTrend)
                .salesBreakdown(salesBreakdown)
                .build();
    }
}
