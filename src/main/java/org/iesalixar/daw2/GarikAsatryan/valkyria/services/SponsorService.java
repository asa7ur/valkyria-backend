package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.SponsorCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.SponsorDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.SponsorDetailDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Sponsor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Stage;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.SponsorMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.SponsorRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.StageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de patrocinadores (sponsors).
 * Proporciona operaciones CRUD completas, gestión de imágenes y asociación con escenarios (stages).
 */
@Service
@RequiredArgsConstructor
public class SponsorService {
    private static final Logger logger = LoggerFactory.getLogger(SponsorService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final SponsorRepository sponsorRepository;
    private final StageRepository stageRepository;
    private final SponsorMapper sponsorMapper;
    private final FileService fileService;
    private final PaginationComponent paginationComponent;

    // Constante que define el directorio donde se almacenan las imágenes de patrocinadores
    private static final String SPONSORS_FOLDER = "sponsors";

    /**
     * Obtiene una lista paginada de patrocinadores basada en filtros.
     * Actualiza el FilterDTO con los metadatos de paginación.
     */
    @Transactional(readOnly = true)
    public List<SponsorDetailDTO> getAllSponsors(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de patrocinadores. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        // Creamos el objeto Pageable usando el componente de paginación
        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        // Ejecutamos la búsqueda (con o sin filtro de texto)
        Page<Sponsor> sponsorPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? sponsorRepository.searchSponsors(filterDTO.getSearch(), pageable)
                : sponsorRepository.findAll(pageable);

        // Actualizamos los metadatos del filtro (total de páginas, elementos, etc.)
        paginationComponent.updateFilterMetadata(filterDTO, sponsorPage);

        logger.debug("Patrocinadores encontrados: {} de {} totales",
                sponsorPage.getNumberOfElements(),
                sponsorPage.getTotalElements());

        // Convertir entidades a DTOs usando el mapper
        return sponsorPage.getContent().stream()
                .map(sponsorMapper::toDetailDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene la lista completa de patrocinadores para selectores.
     */
    @Transactional(readOnly = true)
    public List<SponsorDTO> getAllSponsors() {
        logger.info("Recuperando lista completa de patrocinadores");

        List<SponsorDTO> sponsors = sponsorRepository.findAll().stream()
                .map(sponsorMapper::toDTO)
                .toList();

        logger.debug("Total de patrocinadores recuperados: {}", sponsors.size());
        return sponsors;
    }

    /**
     * Obtiene el detalle de un patrocinador o lanza excepción si no existe.
     */
    @Transactional(readOnly = true)
    public SponsorDetailDTO getSponsorById(Long id) {
        logger.info("Buscando detalle del patrocinador con ID: {}", id);

        return sponsorRepository.findById(id)
                .map(sponsorMapper::toDetailDTO)
                .orElseThrow(() -> new AppException("msg.sponsor.not-found", id));
    }

    @Transactional
    public SponsorDTO createSponsor(SponsorCreateDTO dto) {
        logger.info("Iniciando creación de nuevo patrocinador: {}", dto.getName());

        // Conversión de DTO a entidad
        Sponsor sponsor = sponsorMapper.toEntity(dto);

        // Actualizar relaciones con escenarios (stages) si se proporcionaron
        updateSponsorStages(sponsor, dto.getStageIds());

        // Persistencia en base de datos
        Sponsor savedSponsor = sponsorRepository.save(sponsor);
        logger.info("Patrocinador creado con éxito. ID: {}, Nombre: {}",
                savedSponsor.getId(), savedSponsor.getName());

        return sponsorMapper.toDTO(savedSponsor);
    }

    @Transactional
    public SponsorDTO updateSponsor(Long id, SponsorCreateDTO dto) {
        logger.info("Iniciando actualización del patrocinador con ID: {}", id);

        // Buscar el patrocinador existente
        Sponsor existingSponsor = sponsorRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Patrocinador con ID {} no encontrado para actualización", id);
                    return new AppException("msg.sponsor.not-found", id);
                });

        logger.debug("Patrocinador encontrado: {}. Datos actuales: Nombre={}",
                id, existingSponsor.getName());

        // Actualizar los campos de la entidad con los datos del DTO
        sponsorMapper.updateEntityFromDTO(dto, existingSponsor);
        logger.debug("Datos del patrocinador actualizados desde DTO");

        // Actualizar relaciones con escenarios
        updateSponsorStages(existingSponsor, dto.getStageIds());

        // Guardar cambios
        Sponsor updatedSponsor = sponsorRepository.save(existingSponsor);
        logger.info("Patrocinador con ID {} actualizado correctamente. Nuevo nombre: {}",
                id, updatedSponsor.getName());

        return sponsorMapper.toDTO(updatedSponsor);
    }

    @Transactional
    public String processAndSaveLogo(Long id, MultipartFile file) {
        logger.info("Procesando nueva imagen para patrocinador con ID: {}", id);

        // Buscar el patrocinador
        Sponsor sponsor = sponsorRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Patrocinador con ID {} no encontrado al intentar guardar imagen", id);
                    return new AppException("msg.sponsor.not-found", id);
                });

        // Paso 1: Eliminar la imagen anterior si existe
        if (sponsor.getImage() != null) {
            logger.debug("Eliminando imagen anterior: {}", sponsor.getImage());
            fileService.deleteFile(sponsor.getImage(), SPONSORS_FOLDER);
            logger.info("Imagen anterior eliminada correctamente");
        } else {
            logger.debug("No existía imagen anterior para este patrocinador");
        }

        // Paso 2: Guardar el nuevo archivo físicamente
        logger.debug("Guardando nuevo archivo de imagen...");
        String fileName = fileService.saveFile(file, SPONSORS_FOLDER);
        logger.info("Archivo guardado con nombre: {}", fileName);

        // Paso 3: Actualizar la referencia en la base de datos
        sponsor.setImage(fileName);
        sponsorRepository.save(sponsor);
        logger.info("Imagen actualizada en BD para patrocinador ID: {}. Nueva imagen: {}",
                id, fileName);

        return fileName;
    }

    @Transactional
    public void deleteLogo(Long id) {
        logger.info("Iniciando eliminación de imagen del patrocinador con ID: {}", id);

        // Buscar el patrocinador
        Sponsor sponsor = sponsorRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Patrocinador con ID {} no encontrado al intentar eliminar imagen", id);
                    return new AppException("msg.sponsor.not-found", id);
                });

        // Verificar si realmente tiene una imagen
        if (sponsor.getImage() != null) {
            String imageFileName = sponsor.getImage();
            logger.debug("Eliminando archivo de imagen: {}", imageFileName);

            // Eliminar archivo físico
            fileService.deleteFile(imageFileName, SPONSORS_FOLDER);

            // Eliminar referencia en base de datos
            sponsor.setImage(null);
            sponsorRepository.save(sponsor);

            logger.info("Imagen del patrocinador ID {} eliminada correctamente (archivo: {})",
                    id, imageFileName);
        } else {
            logger.info("El patrocinador ID {} no tiene imagen asignada, no hay nada que eliminar",
                    id);
        }
    }

    @Transactional
    public void deleteSponsor(Long id) {
        logger.info("Iniciando eliminación del patrocinador con ID: {}", id);

        // Buscar el patrocinador
        Sponsor sponsor = sponsorRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Patrocinador con ID {} no encontrado para eliminación", id);
                    return new AppException("msg.sponsor.not-found", id);
                });

        logger.debug("Patrocinador encontrado: {}", sponsor.getName());

        // Paso 1: Eliminar la imagen física si existe
        if (sponsor.getImage() != null) {
            logger.debug("Eliminando imagen asociada: {}", sponsor.getImage());
            fileService.deleteFile(sponsor.getImage(), SPONSORS_FOLDER);
            logger.info("Imagen del patrocinador eliminada del sistema de archivos");
        } else {
            logger.debug("El patrocinador no tiene imagen asociada");
        }

        // Paso 2: Eliminar el registro del patrocinador
        // Las relaciones Many-to-Many se eliminarán automáticamente de la tabla intermedia
        sponsorRepository.delete(sponsor);
        logger.info("Patrocinador con ID {} eliminado correctamente del sistema. " +
                "Relaciones con escenarios también eliminadas", id);
    }

    private void updateSponsorStages(Sponsor sponsor, List<Long> stageIds) {
        if (stageIds != null) {
            logger.debug("Actualizando relación con {} escenarios", stageIds.size());

            // Buscar todos los escenarios por sus IDs
            List<Stage> stages = stageRepository.findAllById(stageIds);

            // Establecer la nueva lista de escenarios
            sponsor.setStages(stages);
            logger.debug("Relación con escenarios actualizada.");
        } else {
            logger.debug("No se proporcionaron IDs de escenarios, se mantiene la relación actual");
        }
    }
}