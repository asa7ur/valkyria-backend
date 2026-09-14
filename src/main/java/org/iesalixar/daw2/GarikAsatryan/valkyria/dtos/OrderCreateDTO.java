package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Este DTO representa la solicitud de compra que llega desde el frontend.
 * Contiene las listas de tickets y campings que el usuario desea adquirir.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreateDTO {

    public static final int MAX_ITEMS_PER_TYPE = 10;

    @NotEmpty(message = "{msg.error.at-least-one}")
    @Size(max = MAX_ITEMS_PER_TYPE, message = "{msg.validation.order.max-items}")
    @Valid
    private List<TicketCreateDTO> tickets = new ArrayList<>();

    @Size(max = MAX_ITEMS_PER_TYPE, message = "{msg.validation.order.max-items}")
    @Valid
    private List<CampingCreateDTO> campings = new ArrayList<>();

    // Obligatorio solo para compras sin sesión (se comprueba en OrderService)
    @Email(message = "{msg.validation.email}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String guestEmail;
}
