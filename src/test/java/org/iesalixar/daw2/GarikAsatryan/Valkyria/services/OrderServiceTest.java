package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderService;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.CampingMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.OrderMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.TicketMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.OrderRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TicketTypeRepository ticketTypeRepository;
    @Mock private CampingTypeRepository campingTypeRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private TicketMapper ticketMapper;
    @Mock private CampingMapper campingMapper;
    @Mock private PaginationComponent paginationComponent;

    @InjectMocks
    private OrderService orderService;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private TicketType makeTicketType(int stock, BigDecimal price) {
        TicketType t = new TicketType();
        t.setId(1L);
        t.setName("General");
        t.setStockAvailable(stock);
        t.setStockTotal(stock);
        t.setPrice(price);
        return t;
    }

    private CampingType makeCampingType(Long id, int stock, BigDecimal price) {
        CampingType c = new CampingType();
        c.setId(id);
        c.setName("Standard");
        c.setStockAvailable(stock);
        c.setStockTotal(stock);
        c.setPrice(price);
        return c;
    }

    private TicketCreateDTO ticketDTO(Long typeId) {
        TicketCreateDTO dto = new TicketCreateDTO();
        dto.setTicketTypeId(typeId);
        dto.setFirstName("Ana");
        dto.setLastName("García");
        dto.setDocumentType(DocumentType.DNI);
        dto.setDocumentNumber("12345678A");
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        return dto;
    }

    private CampingCreateDTO campingDTO(Long typeId) {
        CampingCreateDTO dto = new CampingCreateDTO();
        dto.setCampingTypeId(typeId);
        dto.setFirstName("Carlos");
        dto.setLastName("Pérez");
        dto.setDocumentType(DocumentType.PASSPORT);
        dto.setDocumentNumber("AB123456");
        dto.setBirthDate(LocalDate.of(1988, 5, 20));
        return dto;
    }

    private User registeredUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@test.com");
        return u;
    }

    // ─── executeOrder ──────────────────────────────────────────────────────────

    @Test
    void executeOrder_registeredUser_setsUserAndPendingStatus() {
        TicketType type = makeTicketType(5, new BigDecimal("50.00"));
        TicketCreateDTO tDto = ticketDTO(1L);
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(tDto));

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(ticketMapper.toEntityFromOrder(eq(tDto), eq(type), any(Order.class), anyString()))
                .thenReturn(new Ticket());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.executeOrder(request, registeredUser());

        assertThat(result.getUser().getEmail()).isEqualTo("user@test.com");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void executeOrder_guestUser_setsGuestEmailAndNullUser() {
        TicketType type = makeTicketType(5, new BigDecimal("50.00"));
        TicketCreateDTO tDto = ticketDTO(1L);
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(tDto));
        request.setGuestEmail("guest@example.com");

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(ticketMapper.toEntityFromOrder(any(), any(), any(), any())).thenReturn(new Ticket());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.executeOrder(request, null);

        assertThat(result.getUser()).isNull();
        assertThat(result.getGuestEmail()).isEqualTo("guest@example.com");
    }

    @Test
    void executeOrder_ticketTypeNotFound_throwsAppException() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(ticketDTO(99L)));

        when(ticketTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.ticket-type-not-found");
    }

    @Test
    void executeOrder_ticketOutOfStock_throwsAppException() {
        TicketType type = makeTicketType(0, new BigDecimal("50.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(ticketDTO(1L)));

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.no-stock");
    }

    @Test
    void executeOrder_campingTypeNotFound_throwsAppException() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of());
        request.setCampings(List.of(campingDTO(99L)));

        when(campingTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.camping-type-not-found");
    }

    @Test
    void executeOrder_campingOutOfStock_throwsAppException() {
        CampingType type = makeCampingType(1L, 0, new BigDecimal("100.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of());
        request.setCampings(List.of(campingDTO(1L)));

        when(campingTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.no-stock");
    }

    @Test
    void executeOrder_requestedTicketsExceedStock_throwsAppExceptionImmediately() {
        TicketType type = makeTicketType(2, new BigDecimal("50.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(ticketDTO(1L), ticketDTO(1L), ticketDTO(1L))); // 3 solicitados, 2 disponibles

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.no-stock");

        // El mapper nunca debe llamarse: el error ocurre antes de procesar items
        verify(ticketMapper, never()).toEntityFromOrder(any(), any(), any(), any());
    }

    @Test
    void executeOrder_requestedCampingsExceedStock_throwsAppExceptionImmediately() {
        CampingType type = makeCampingType(1L, 1, new BigDecimal("100.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of());
        request.setCampings(List.of(campingDTO(1L), campingDTO(1L))); // 2 solicitados, 1 disponible

        when(campingTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.no-stock");

        // El mapper nunca debe llamarse: el error ocurre antes de procesar items
        verify(campingMapper, never()).toEntityFromOrder(any(), any(), any(), any());
    }

    @Test
    void executeOrder_mixedItems_sumsPricesCorrectly() {
        TicketType tType = makeTicketType(5, new BigDecimal("50.00"));
        CampingType cType = makeCampingType(2L, 3, new BigDecimal("100.00"));

        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(ticketDTO(1L)));
        request.setCampings(List.of(campingDTO(2L)));

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(tType));
        when(ticketMapper.toEntityFromOrder(any(), any(), any(), any())).thenReturn(new Ticket());
        when(campingTypeRepository.findById(2L)).thenReturn(Optional.of(cType));
        when(campingMapper.toEntityFromOrder(any(), any(), any(), any())).thenReturn(new Camping());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.executeOrder(request, registeredUser());

        assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void executeOrder_decrementsTicketStockByOne() {
        TicketType type = makeTicketType(5, new BigDecimal("50.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of(ticketDTO(1L)));

        when(ticketTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(ticketMapper.toEntityFromOrder(any(), any(), any(), any())).thenReturn(new Ticket());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.executeOrder(request, registeredUser());

        assertThat(type.getStockAvailable()).isEqualTo(4);
        verify(ticketTypeRepository).save(type);
    }

    @Test
    void executeOrder_decrementsCampingStockByOne() {
        CampingType type = makeCampingType(1L, 3, new BigDecimal("100.00"));
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of());
        request.setCampings(List.of(campingDTO(1L)));

        when(campingTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(campingMapper.toEntityFromOrder(any(), any(), any(), any())).thenReturn(new Camping());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.executeOrder(request, registeredUser());

        assertThat(type.getStockAvailable()).isEqualTo(2);
        verify(campingTypeRepository).save(type);
    }

    @Test
    void executeOrder_noItems_totalPriceIsZero() {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setTickets(List.of());
        request.setCampings(List.of());
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.executeOrder(request, registeredUser());

        assertThat(result.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── deleteOrder ───────────────────────────────────────────────────────────

    @Test
    void deleteOrder_orderNotFound_throwsAppException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.order-not-found");
    }

    @Test
    void deleteOrder_restoresTicketStockAndDeletesOrder() {
        TicketType type = makeTicketType(2, new BigDecimal("50.00"));
        Ticket ticket = new Ticket();
        ticket.setTicketType(type);

        Order order = new Order();
        order.setId(1L);
        order.getTickets().add(ticket);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(1L);

        assertThat(type.getStockAvailable()).isEqualTo(3);
        verify(ticketTypeRepository).save(type);
        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_restoresCampingStockAndDeletesOrder() {
        CampingType type = makeCampingType(1L, 1, new BigDecimal("100.00"));
        Camping camping = new Camping();
        camping.setCampingType(type);

        Order order = new Order();
        order.setId(2L);
        order.getCampings().add(camping);

        when(orderRepository.findById(2L)).thenReturn(Optional.of(order));

        orderService.deleteOrder(2L);

        assertThat(type.getStockAvailable()).isEqualTo(2);
        verify(campingTypeRepository).save(type);
        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_ticketWithNullType_doesNotSaveTicketType() {
        Ticket ticket = new Ticket();
        ticket.setTicketType(null);

        Order order = new Order();
        order.setId(3L);
        order.getTickets().add(ticket);

        when(orderRepository.findById(3L)).thenReturn(Optional.of(order));

        assertThatNoException().isThrownBy(() -> orderService.deleteOrder(3L));
        verify(ticketTypeRepository, never()).save(any());
        verify(orderRepository).delete(order);
    }

    // ─── confirmPayment ────────────────────────────────────────────────────────

    @Test
    void confirmPayment_orderNotFound_throwsAppException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmPayment(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.order-not-found");
    }

    @Test
    void confirmPayment_pendingOrder_updatesStatusToPaid() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PENDING);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.confirmPayment(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
    }

    @Test
    void confirmPayment_alreadyPaid_isIdempotent() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.PAID);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.confirmPayment(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, never()).save(any());
    }

    // ─── getOrdersByUser ───────────────────────────────────────────────────────

    @Test
    void getOrdersByUser_returnsOrdersMappedToDTOs() {
        Order order = new Order();
        order.setId(5L);
        OrderDTO dto = new OrderDTO();
        dto.setId(5L);

        when(orderRepository.findByUserEmailOrderByOrderDateDesc("user@test.com"))
                .thenReturn(List.of(order));
        when(orderMapper.toDTOList(List.of(order))).thenReturn(List.of(dto));

        List<OrderDTO> result = orderService.getOrdersByUser("user@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(5L);
    }

    @Test
    void getOrdersByUser_noOrders_returnsEmptyList() {
        when(orderRepository.findByUserEmailOrderByOrderDateDesc("nobody@test.com"))
                .thenReturn(List.of());
        when(orderMapper.toDTOList(List.of())).thenReturn(List.of());

        List<OrderDTO> result = orderService.getOrdersByUser("nobody@test.com");

        assertThat(result).isEmpty();
    }

    // ─── getOrderEntityById ────────────────────────────────────────────────────

    @Test
    void getOrderEntityById_found_returnsOrder() {
        Order order = new Order();
        order.setId(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        Order result = orderService.getOrderEntityById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getOrderEntityById_notFound_throwsAppException() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderEntityById(42L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.error.order-not-found");
    }

    // ─── getAllOrders ──────────────────────────────────────────────────────────

    @Test
    void getAllOrders_withoutSearch_callsFindAll() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);

        Pageable pageable = PageRequest.of(0, 10);
        Order order = new Order();
        Page<Order> page = new PageImpl<>(List.of(order));
        OrderDTO dto = new OrderDTO();

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(orderRepository.findAll(pageable)).thenReturn(page);
        when(orderMapper.toDTO(order)).thenReturn(dto);

        List<OrderDTO> result = orderService.getAllOrders(filter);

        assertThat(result).hasSize(1);
        verify(orderRepository).findAll(pageable);
        verify(orderRepository, never()).searchOrders(any(), any());
    }

    @Test
    void getAllOrders_withSearch_callsSearchOrders() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        filter.setSearch("John");

        Pageable pageable = PageRequest.of(0, 10);
        Page<Order> page = new PageImpl<>(List.of());

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(orderRepository.searchOrders("John", pageable)).thenReturn(page);

        List<OrderDTO> result = orderService.getAllOrders(filter);

        assertThat(result).isEmpty();
        verify(orderRepository).searchOrders("John", pageable);
        verify(orderRepository, never()).findAll(any(Pageable.class));
    }
}
