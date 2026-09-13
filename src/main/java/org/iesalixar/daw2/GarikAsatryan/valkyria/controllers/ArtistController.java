package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.ArtistService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/artists")
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ResponseDTO<List<ArtistDTO>>> getAllArtists(@ModelAttribute FilterDTO filterDTO) {
        List<ArtistDTO> data = artistService.getAllArtists(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.list.success"), data, filterDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseDTO<ArtistDetailDTO>> getArtistById(@PathVariable Long id) {
        ArtistDetailDTO data = artistService.getArtistById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.get.success"), data));
    }

    @GetMapping("/logo")
    public ResponseEntity<ResponseDTO<List<ArtistLogoDTO>>> getArtistLogo() {
        List<ArtistLogoDTO> logos = artistService.getArtistLogo();
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.logo.list.success"), logos));
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<ArtistDTO>> createArtist(@Valid @RequestBody ArtistCreateDTO artistCreateDTO) {
        ArtistDTO created = artistService.createArtist(artistCreateDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.artist.create.success"), created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<ArtistDTO>> updateArtist(
            @PathVariable Long id,
            @Valid @RequestBody ArtistCreateDTO artistCreateDTO) {
        ArtistDTO updated = artistService.updateArtist(id, artistCreateDTO);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.update.success"), updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteArtist(@PathVariable Long id) {
        artistService.deleteArtist(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.delete.success"), null));
    }

    @PostMapping("/{id}/logo")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Map<String, String>>> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String baseName = artistService.processAndSaveLogo(id, file);
        return ResponseEntity.ok(ResponseDTO.success(
                getMessage("msg.artist.logo.upload.success"),
                Map.of("fileName", baseName)
        ));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<List<ArtistImageDTO>>> uploadGalleryImages(
            @PathVariable Long id,
            @RequestParam("files") MultipartFile[] files) {
        List<ArtistImageDTO> newImages = artistService.uploadArtistImages(id, files);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.artist.images.upload.success"), newImages));
    }

    @DeleteMapping("/{id}/logo")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteLogo(@PathVariable Long id) {
        artistService.deleteLogo(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.logo.delete.success"), null));
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<Void>> deleteGalleryImage(@PathVariable Long imageId) {
        artistService.deleteArtistImage(imageId);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.artist.images.delete.success"), null));
    }

    /**
     * Utilidad para obtener mensajes traducidos según el locale de la petición.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}