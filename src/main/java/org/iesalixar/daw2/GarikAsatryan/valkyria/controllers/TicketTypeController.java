package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketTypeCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketTypeDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.TicketTypeService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ticket-types")
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<TicketTypeDTO>>> getAllTicketTypes(@ModelAttribute FilterDTO filterDTO) {
        List<TicketTypeDTO> data = ticketTypeService.getAllTicketTypes(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticketType.list.success"), data, filterDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<TicketTypeDTO>> getTicketTypeById(@PathVariable Long id) {
        TicketTypeDTO data = ticketTypeService.getTicketTypeById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticketType.get.success"), data));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<TicketTypeDTO>> createTicketType(@Valid @RequestBody TicketTypeCreateDTO dto) {
        TicketTypeDTO created = ticketTypeService.createTicketType(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.ticketType.create.success"), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<TicketTypeDTO>> updateTicketType(
            @PathVariable Long id,
            @Valid @RequestBody TicketTypeCreateDTO dto) {
        TicketTypeDTO updated = ticketTypeService.updateTicketType(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticketType.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteTicketType(@PathVariable Long id) {
        ticketTypeService.deleteTicketType(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.ticketType.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
