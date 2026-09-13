package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.TicketService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
// Los tickets contienen datos personales y códigos QR de acceso: gestión reservada a MANAGER/ADMIN.
@PreAuthorize("hasRole('MANAGER')")
public class TicketController {

    private final TicketService ticketService;
    private final MessageSource messageSource;

    /**
     * Obtiene una página de tickets.
     * Permite filtrar por término de búsqueda (nombre, documento, tipo, estado).
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<TicketDTO>>> getAllTickets(
            @ModelAttribute FilterDTO filterDTO) {
        List<TicketDTO> data = ticketService.getAllTickets(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticket.list.success"), data, filterDTO));
    }

    /**
     * Obtiene la información detallada de un ticket específico.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<TicketDTO>> getTicketById(@PathVariable Long id) {
        TicketDTO data = ticketService.getTicketById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticket.get.success"), data));
    }

    /**
     * Crea un nuevo ticket.
     * La validación @IsAdult se dispara aquí automáticamente.
     */
    @PostMapping
    public ResponseEntity<ResponseDTO<TicketDTO>> createTicket(@Valid @RequestBody TicketCreateDTO dto) {
        TicketDTO created = ticketService.createTicket(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.ticket.create.success"), created));
    }

    /**
     * Actualiza los datos de un ticket existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ResponseDTO<TicketDTO>> updateTicket(
            @PathVariable Long id,
            @Valid @RequestBody TicketCreateDTO dto) {
        TicketDTO updated = ticketService.updateTicket(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticket.update.success"), updated));
    }

    /**
     * Elimina un ticket del sistema.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticket.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
