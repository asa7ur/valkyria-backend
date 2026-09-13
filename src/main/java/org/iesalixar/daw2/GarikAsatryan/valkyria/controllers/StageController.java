package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.ResponseDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.StageCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.StageDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.StageService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stages")
@RequiredArgsConstructor
public class StageController {

    private final StageService stageService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<StageDTO>>> getAllStages(
            @ModelAttribute FilterDTO filterDTO) {
        List<StageDTO> data = stageService.getAllStages(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.stage.list.success"), data, filterDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<StageDTO>> getStageById(@PathVariable Long id) {
        StageDTO data = stageService.getStageById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.stage.get.success"), data));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<StageDTO>> createStage(@Valid @RequestBody StageCreateDTO stageCreateDTO) {
        StageDTO created = stageService.createStage(stageCreateDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.stage.create.success"), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<StageDTO>> updateStage(
            @PathVariable Long id,
            @Valid @RequestBody StageCreateDTO dto) {
        StageDTO updated = stageService.updateStage(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.stage.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteStage(@PathVariable Long id) {
        stageService.deleteStage(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.stage.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}