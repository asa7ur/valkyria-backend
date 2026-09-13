package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.PerformanceCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.PerformanceDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.PerformanceService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/performances")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final MessageSource messageSource;

    /**
     * Obtiene todas las actuaciones con paginación y búsqueda.
     * GET /api/v1/performances?search=rock&page=0&size=10
     */
    @GetMapping
    public ResponseEntity<ResponseDTO<List<PerformanceDTO>>> getAllPerformances(
            @ModelAttribute FilterDTO filterDTO) {
        List<PerformanceDTO> data = performanceService.getAllPerformances(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.performance.list.success"), data, filterDTO));
    }

    @GetMapping("/all")
    public ResponseEntity<ResponseDTO<List<PerformanceDTO>>> getPerformances() {
        List<PerformanceDTO> data = performanceService.getAllPerformances();
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.performance.list.success"), data));
    }

    /**
     * Obtiene una actuación por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<PerformanceDTO>> getPerformanceById(@PathVariable Long id) {
        PerformanceDTO data = performanceService.getPerformanceById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.performance.get.success"), data));
    }

    /**
     * Crea una nueva actuación.
     * El @Valid disparará FieldsComparison y PerformanceOverlap.
     */
    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<PerformanceDTO>> createPerformance(
            @Valid @RequestBody PerformanceCreateDTO dto) {
        PerformanceDTO created = performanceService.createPerformance(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.performance.create.success"), created));
    }

    /**
     * Actualiza una actuación existente.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<PerformanceDTO>> updatePerformance(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceCreateDTO dto) {
        PerformanceDTO updated = performanceService.updatePerformance(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.performance.update.success"), updated));
    }

    /**
     * Elimina una actuación.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deletePerformance(@PathVariable Long id) {
        performanceService.deletePerformance(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.performance.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}