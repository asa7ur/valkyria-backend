package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.CampingTypeService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/camping-types")
@RequiredArgsConstructor
public class CampingTypeController {

    private final CampingTypeService campingTypeService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<CampingTypeDTO>>> getAllCampingTypes(@ModelAttribute FilterDTO filterDTO) {
        List<CampingTypeDTO> data = campingTypeService.getAllCampingTypes(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.campingType.list.success"), data, filterDTO));

    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<CampingTypeDTO>> getCampingTypeById(@PathVariable Long id) {
        CampingTypeDTO data = campingTypeService.getCampingTypeById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.campingType.get.success"), data));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<CampingTypeDTO>> createCampingType(@Valid @RequestBody CampingTypeCreateDTO dto) {
        CampingTypeDTO created = campingTypeService.createCampingType(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.campingType.create.success"), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<CampingTypeDTO>> updateCampingType(
            @PathVariable Long id,
            @Valid @RequestBody CampingTypeCreateDTO dto) {
        CampingTypeDTO updated = campingTypeService.updateCampingType(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.campingType.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteCampingType(@PathVariable Long id) {
        campingTypeService.deleteCampingType(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.campingType.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}