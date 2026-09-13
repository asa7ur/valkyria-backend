package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.SponsorService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sponsors")
@RequiredArgsConstructor
public class SponsorController {

    private final SponsorService sponsorService;
    private final MessageSource messageSource;

    @GetMapping("/all")
    public ResponseEntity<ResponseDTO<List<SponsorDTO>>> getSponsors() {
        List<SponsorDTO> data = sponsorService.getAllSponsors();
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.list.success"), data));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<List<SponsorDetailDTO>>> getAllSponsors(@ModelAttribute FilterDTO filterDTO) {
        List<SponsorDetailDTO> data = sponsorService.getAllSponsors(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.list.success"), data, filterDTO));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<SponsorDetailDTO>> getSponsorById(@PathVariable Long id) {
        SponsorDetailDTO data = sponsorService.getSponsorById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.get.success"), data));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<SponsorDTO>> createSponsor(@Valid @RequestBody SponsorCreateDTO dto) {
        SponsorDTO created = sponsorService.createSponsor(dto);
        return ResponseEntity.status(HttpStatus.CREATED).
                body(ResponseDTO.success(getMessage("msg.sponsor.create.success"), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<SponsorDTO>> updateSponsor(
            @PathVariable Long id,
            @Valid @RequestBody SponsorCreateDTO dto) {
        SponsorDTO updated = sponsorService.updateSponsor(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteSponsor(@PathVariable Long id) {
        sponsorService.deleteSponsor(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.delete.success"), null));
    }

    @PostMapping("/{id}/logo")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Map<String, String>>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String fileName = sponsorService.processAndSaveLogo(id, file);
        return ResponseEntity.ok(ResponseDTO.success(
                getMessage("msg.sponsor.image.upload.success"),
                Map.of("fileName", fileName)
        ));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteLogo(@PathVariable Long id) {
        sponsorService.deleteLogo(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.sponsor.image.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}