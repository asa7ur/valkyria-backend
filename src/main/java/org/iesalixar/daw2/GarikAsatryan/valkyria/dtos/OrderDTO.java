package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import lombok.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para devolver la información completa de un pedido.
 * Se usa tanto en el historial de pedidos como tras finalizar una compra.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderDTO {
    private Long id;
    private LocalDateTime orderDate;
    private BigDecimal totalPrice;
    private OrderStatus status;
    private String guestEmail;
    private String userEmail;
    private List<TicketDTO> tickets;
    private List<CampingDTO> campings;
}