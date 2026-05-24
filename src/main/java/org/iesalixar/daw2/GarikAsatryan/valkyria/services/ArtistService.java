package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Artist;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.ArtistImage;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.ArtistMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.ArtistImageRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.ArtistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de ARTISTAS.
 * Proporciona operaciones CRUD completas con validación de artistas.
 */
@Service
@RequiredArgsConstructor
public class ArtistService {
    private static final Logger logger = LoggerFactory.getLogger(ArtistService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final ArtistRepository artistRepository;
    private final ArtistImageRepository artistImageRepository;
    private final ArtistMapper artistMapper;
    private final FileService fileService;
    private final PaginationComponent paginationComponent;

    private static final String ARTISTS_FOLDER = "artists";

    /**
     * Obtiene una lista de artistas basada en filtros.
     * Actualiza el FilterDTO con los metadatos de paginación.
     */
    @Transactional(readOnly = true)
    public List<ArtistDTO> getAllArtists(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de artistas. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch().replaceAll("[\r\n]", "_") : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<Artist> artistPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? artistRepository.searchArtists(filterDTO.getSearch(), pageable)
                : artistRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, artistPage);

        logger.debug("Artistas encontrados: {} de {} totales",
                artistPage.getNumberOfElements(),
                artistPage.getTotalElements());

        return artistPage.getContent().stream()
                .map(artistMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene el detalle de un artista o lanza excepción si no existe.
     */
    @Transactional(readOnly = true)
    public ArtistDetailDTO getArtistById(Long id) {
        return artistRepository.findById(id)
                .map(artistMapper::toDetailDTO)
                .orElseThrow(() -> new AppException("msg.artist.not-found", id));
    }

    @Transactional(readOnly = true)
    public List<ArtistLogoDTO> getArtistLogo() {
        List<Artist> artists = artistRepository.findByLogoIsNotNull();
        return artistMapper.toLogoDTOList(artists);
    }

    @Transactional
    public ArtistDTO createArtist(ArtistCreateDTO artistCreateDTO) {
        if (artistRepository.existsByEmail(artistCreateDTO.getEmail())) {
            throw new AppException("msg.artist.email-exists", artistCreateDTO.getEmail());
        }
        Artist artist = artistMapper.toEntity(artistCreateDTO);
        Artist savedArtist = artistRepository.save(artist);
        return artistMapper.toDTO(savedArtist);
    }

    @Transactional
    public ArtistDTO updateArtist(Long id, ArtistCreateDTO artistCreateDTO) {
        Artist existingArtist = artistRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.artist.not-found", id));

        if (artistRepository.existsByEmailAndIdNot(artistCreateDTO.getEmail(), id)) {
            throw new AppException("msg.artist.email-exists", artistCreateDTO.getEmail());
        }

        artistMapper.updateEntityFromDTO(artistCreateDTO, existingArtist);
        Artist updatedArtist = artistRepository.save(existingArtist);
        return artistMapper.toDTO(updatedArtist);
    }

    @Transactional
    public String processAndSaveLogo(Long id, MultipartFile file) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.artist.not-found", id));

        if (artist.getLogo() != null) {
            fileService.deleteFile(artist.getLogo(), ARTISTS_FOLDER);
        }

        String fileName = fileService.saveFile(file, ARTISTS_FOLDER);
        artist.setLogo(fileName);
        artistRepository.save(artist);
        return fileName;
    }

    @Transactional
    public List<ArtistImageDTO> uploadArtistImages(Long artistId, MultipartFile[] files) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException("msg.artist.not-found", artistId));

        List<ArtistImage> newImages = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String fileName = fileService.saveFile(file, ARTISTS_FOLDER);
            ArtistImage image = new ArtistImage();
            image.setImageUrl(fileName);
            image.setArtist(artist);
            newImages.add(image);
        }

        List<ArtistImage> savedImages = artistImageRepository.saveAll(newImages);
        return artistMapper.toImageDTOList(savedImages);
    }

    @Transactional
    public void deleteArtist(Long id) {
        if (!artistRepository.existsById(id)) {
            throw new AppException("msg.artist.not-found", id);
        }
        artistRepository.deleteById(id);
    }

    @Transactional
    public void deleteLogo(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.artist.not-found", id));

        if (artist.getLogo() != null) {
            fileService.deleteFile(artist.getLogo(), ARTISTS_FOLDER);
            artist.setLogo(null);
            artistRepository.save(artist);
        }
    }

    @Transactional
    public void deleteArtistImage(Long imageId) {
        artistImageRepository.findById(imageId).ifPresentOrElse(
                image -> {
                    fileService.deleteFile(image.getImageUrl(), ARTISTS_FOLDER);
                    artistImageRepository.delete(image);
                },
                () -> {
                    throw new AppException("msg.image.not-found", imageId);
                }
        );
    }
}