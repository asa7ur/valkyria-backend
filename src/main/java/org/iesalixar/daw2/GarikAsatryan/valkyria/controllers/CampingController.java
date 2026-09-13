package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.CampingService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/campings")
@RequiredArgsConstructor
// Las reservas de camping contienen datos personales y códigos QR de acceso: gestión reservada a MANAGER/ADMIN.
@PreAuthorize("hasRole('MANAGER')")
public class CampingController {

    private final CampingService campingService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<CampingDTO>>> getAllCampings(@ModelAttribute FilterDTO filterDTO) {
        List<CampingDTO> data = campingService.getAllCampings(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.camping.list.success"), data, filterDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<CampingDTO>> getCampingById(@PathVariable Long id) {
        CampingDTO data = campingService.getCampingById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.camping.get.success"), data));
    }

    @PostMapping
    public ResponseEntity<ResponseDTO<CampingDTO>> createCamping(@Valid @RequestBody CampingCreateDTO dto) {
        CampingDTO created = campingService.createCamping(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.camping.create.success"), created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResponseDTO<CampingDTO>> updateCamping(
            @PathVariable Long id,
            @Valid @RequestBody CampingCreateDTO dto) {
        CampingDTO updated = campingService.updateCamping(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.camping.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ResponseDTO<Void>> deleteCamping(@PathVariable Long id) {
        campingService.deleteCamping(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.camping.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
