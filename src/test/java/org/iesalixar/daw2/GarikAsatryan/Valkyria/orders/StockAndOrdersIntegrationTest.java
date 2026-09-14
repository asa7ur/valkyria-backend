package org.iesalixar.daw2.GarikAsatryan.Valkyria.orders;

import com.jayway.jsonpath.JsonPath;
import org.iesalixar.daw2.GarikAsatryan.Valkyria.AbstractIntegrationTest;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.OrderCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.DocumentType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockAndOrdersIntegrationTest extends AbstractIntegrationTest {

    private static final String API = "/api/v1";

    @Autowired
    private OrderService orderService;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Test
    void concurrentPurchasesOfLastTicket_onlyOneSucceeds() throws Exception {
        TicketType type = newTicketType(1);
        int buyers = 5;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(buyers);

        List<Future<HttpStatus>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.executeOrder(guestOrder(type.getId(), 1), null);
                    return HttpStatus.OK;
                } catch (AppException e) {
                    return e.getStatus();
                }
            }));
        }
        start.countDown();

        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<HttpStatus> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(HttpStatus.OK);
        assertThat(statuses).filteredOn(s -> s == HttpStatus.CONFLICT).hasSize(buyers - 1);
        assertThat(ticketTypeRepository.findById(type.getId()).orElseThrow().getStockAvailable()).isZero();
    }

    @Test
    void failedOrder_rollsBackReservationsOfOtherTypes() {
        TicketType available = newTicketType(5);
        TicketType soldOut = newTicketType(0);
        OrderCreateDTO request = guestOrder(available.getId(), 2);
        request.getTickets().add(ticket(soldOut.getId()));

        assertThatThrownBy(() -> orderService.executeOrder(request, null))
                .isInstanceOf(AppException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);

        assertThat(ticketTypeRepository.findById(available.getId()).orElseThrow().getStockAvailable()).isEqualTo(5);
    }

    @Test
    void adminCreatedTicket_hasLongQrCode_andMovesStock() throws Exception {
        TicketType type = newTicketType(3);
        String admin = tokenFor("ADMIN");

        String response = mockMvc.perform(post(API + "/tickets")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson(type.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.qrCode").value(org.hamcrest.Matchers.matchesPattern("TKT-[0-9A-F]{32}")))
                .andReturn().getResponse().getContentAsString();
        assertThat(stock(type)).isEqualTo(2);

        Integer ticketId = JsonPath.read(response, "$.data.id");
        mockMvc.perform(delete(API + "/tickets/" + ticketId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(stock(type)).isEqualTo(3);
    }

    @Test
    void adminCreatedTicket_withoutStock_returns409() throws Exception {
        TicketType type = newTicketType(0);

        mockMvc.perform(post(API + "/tickets")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson(type.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void checkout_anonymousWithoutGuestEmail_returns400() throws Exception {
        TicketType type = newTicketType(5);

        mockMvc.perform(post(API + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tickets\":[" + ticketJson(type.getId()) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indica un correo electrónico para enviarte las entradas."));
        assertThat(stock(type)).isEqualTo(5);
    }

    @Test
    void checkout_moreThanMaxItems_returns400() throws Exception {
        TicketType type = newTicketType(50);
        String tickets = String.join(",", IntStream.rangeClosed(0, OrderCreateDTO.MAX_ITEMS_PER_TYPE)
                .mapToObj(i -> ticketJson(type.getId())).toList());

        mockMvc.perform(post(API + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestEmail\":\"guest@valkyria.test\",\"tickets\":[" + tickets + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.tickets").exists());
    }

    @Test
    void orderSearch_findsGuestOrdersByEmail() throws Exception {
        // Pedido de invitado incluido en los datos semilla (db/seed)
        mockMvc.perform(get(API + "/orders").param("search", "pedro.garcia")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].guestEmail").value("pedro.garcia@gmail.com"));
    }

    private TicketType newTicketType(int stock) {
        TicketType type = new TicketType();
        type.setName("Test " + UUID.randomUUID().toString().substring(0, 8));
        type.setPrice(new BigDecimal("10.00"));
        type.setStockTotal(Math.max(stock, 5));
        type.setStockAvailable(stock);
        return ticketTypeRepository.save(type);
    }

    private int stock(TicketType type) {
        return ticketTypeRepository.findById(type.getId()).orElseThrow().getStockAvailable();
    }

    private OrderCreateDTO guestOrder(Long ticketTypeId, int quantity) {
        OrderCreateDTO request = new OrderCreateDTO();
        request.setGuestEmail("guest@valkyria.test");
        request.setTickets(new ArrayList<>(IntStream.range(0, quantity).mapToObj(i -> ticket(ticketTypeId)).toList()));
        return request;
    }

    private TicketCreateDTO ticket(Long ticketTypeId) {
        return new TicketCreateDTO("Test", "Buyer", DocumentType.DNI, "12345678Z", LocalDate.of(1990, 1, 1), ticketTypeId);
    }

    private String ticketJson(Long ticketTypeId) {
        return """
                {"firstName":"Test","lastName":"Buyer","documentType":"DNI","documentNumber":"12345678Z",
                 "birthDate":"1990-01-01","ticketTypeId":%d}""".formatted(ticketTypeId);
    }
}
