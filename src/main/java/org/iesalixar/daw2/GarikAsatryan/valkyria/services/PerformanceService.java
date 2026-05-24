package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.PerformanceCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.PerformanceDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Artist;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Performance;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Stage;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.PerformanceMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.ArtistRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.PerformanceRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.StageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de actuaciones (performances).
 * Gestiona el calendario de actuaciones del festival, asociando artistas con escenarios en horarios específicos.
 * <p>
 * Responsabilidades principales:
 * - Programación de actuaciones con artistas y escenarios
 * - Validación de solapamientos horarios en el mismo escenario
 * - Consultas del programa del festival (completo o por búsqueda)
 * - Actualización del lineup del evento
 * <p>
 * IMPORTANTE: Implementa validación de conflictos horarios para evitar que:
 * - Dos artistas actúen en el mismo escenario al mismo tiempo
 * - Se programen actuaciones con horarios superpuestos
 */
@Service
@RequiredArgsConstructor
public class PerformanceService {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final PerformanceRepository performanceRepository;
    private final ArtistRepository artistRepository;
    private final StageRepository stageRepository;
    private final PerformanceMapper performanceMapper;
    private final PaginationComponent paginationComponent;

    /**
     * Obtiene actuaciones paginadas con búsqueda opcional.
     * Permite filtrar por nombre de artista o nombre de escenario.
     * Utilizado para mostrar el programa del festival de forma paginada.
     */
    @Transactional(readOnly = true)
    public List<PerformanceDTO> getAllPerformances(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de actuaciones. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<Performance> performancePage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? performanceRepository.searchPerformances(filterDTO.getSearch(), pageable)
                : performanceRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, performancePage);

        logger.debug("Actuaciones encontradas: {} de {} totales",
                performancePage.getNumberOfElements(),
                performancePage.getTotalElements());

        // Convertir entidades a DTOs usando el mapper
        return performancePage.getContent().stream()
                .map(performanceMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PerformanceDTO> getAllPerformances() {
        logger.info("Recuperando lista completa de actuaciones");

        List<PerformanceDTO> performances = performanceRepository.findAll().stream()
                .map(performanceMapper::toDTO)
                .toList();

        logger.debug("Total de actuaciones recuperadas: {}", performances.size());
        return performances;
    }

    /**
     * Obtiene el detalle de una actuación específica por su ID.
     * Incluye información completa del artista y escenario asociados.
     */
    @Transactional(readOnly = true)
    public PerformanceDTO getPerformanceById(Long id) {
        return performanceRepository.findById(id)
                .map(performanceMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.performance.not-found", id));
    }

    /**
     * Crea una nueva actuación en el programa del festival.
     * Valida que no haya solapamientos horarios antes de crear.
     * <p>
     * Proceso de validación:
     * 1. Verifica que no existan actuaciones en el mismo escenario con horarios superpuestos
     * 2. Valida que el artista exista en el sistema
     * 3. Valida que el escenario exista en el sistema
     * 4. Crea la relación entre artista y escenario en el horario especificado
     *
     * @param dto DTO con los datos de la actuación a crear
     * @return DTO de la actuación creada
     * @throws AppException si hay solapamiento horario, o artista/escenario no existen
     */
    @Transactional
    public PerformanceDTO createPerformance(PerformanceCreateDTO dto) {
        logger.info("Iniciando creación de actuación. Artista ID: {}, Escenario ID: {}, Horario: {} - {}",
                dto.getArtistId(), dto.getStageId(), dto.getStartTime(), dto.getEndTime());

        // Paso 1: Validar que no haya solapamientos horarios
        // currentId = null porque es una creación (no hay que excluir ninguna actuación)
        logger.debug("Validando solapamientos horarios...");
        checkOverlap(dto, null);
        logger.debug("✓ No se encontraron solapamientos horarios");

        // Paso 2: Buscar y validar que el artista existe
        Artist artist = artistRepository.findById(dto.getArtistId())
                .orElseThrow(() -> {
                    logger.error("Artista con ID {} no encontrado al crear actuación", dto.getArtistId());
                    return new AppException("msg.artist.not-found", dto.getArtistId());
                });
        logger.debug("Artista encontrado: {}", artist.getName());

        // Paso 3: Buscar y validar que el escenario existe
        Stage stage = stageRepository.findById(dto.getStageId())
                .orElseThrow(() -> {
                    logger.error("Escenario con ID {} no encontrado al crear actuación", dto.getStageId());
                    return new AppException("msg.stage.not-found", dto.getStageId());
                });
        logger.debug("Escenario encontrado: {}", stage.getName());

        // Paso 4: Crear la entidad Performance y establecer relaciones
        Performance performance = performanceMapper.toEntity(dto);
        performance.setArtist(artist);
        performance.setStage(stage);
        logger.debug("Entidad Performance mapeada y relaciones establecidas");

        // Paso 5: Persistir en base de datos
        Performance saved = performanceRepository.save(performance);

        logger.info("✓ Actuación creada exitosamente. ID: {}, Artista: '{}' en Escenario: '{}', Horario: {} - {}",
                saved.getId(),
                artist.getName(),
                stage.getName(),
                dto.getStartTime(),
                dto.getEndTime());

        return performanceMapper.toDTO(saved);
    }

    /**
     * Actualiza una actuación existente en el programa del festival.
     * Valida que los nuevos horarios no generen solapamientos (excluyendo la actuación actual).
     * <p>
     * Proceso:
     * 1. Verifica que la actuación existe
     * 2. Valida solapamientos horarios (excluyendo esta actuación de la búsqueda)
     * 3. Valida que el nuevo artista (si cambió) existe
     * 4. Valida que el nuevo escenario (si cambió) existe
     * 5. Actualiza los datos y relaciones
     *
     * @param id  ID de la actuación a actualizar
     * @param dto DTO con los nuevos datos
     * @return DTO de la actuación actualizada
     * @throws AppException si hay solapamiento, o entidades no encontradas
     */
    @Transactional
    public PerformanceDTO updatePerformance(Long id, PerformanceCreateDTO dto) {
        logger.info("Iniciando actualización de actuación con ID: {}", id);

        // Paso 1: Buscar la actuación existente
        Performance existingPerformance = performanceRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Actuación con ID {} no encontrada para actualización", id);
                    return new AppException("msg.performance.not-found", id);
                });

        logger.debug("Actuación encontrada. Datos actuales: Artista={}, Escenario={}, Horario={} - {}",
                existingPerformance.getArtist().getName(),
                existingPerformance.getStage().getName(),
                existingPerformance.getStartTime(),
                existingPerformance.getEndTime());

        // Paso 2: Validar solapamiento horario
        // Importante: pasamos el ID actual para excluirlo de la búsqueda de conflictos
        logger.debug("Validando solapamientos horarios (excluyendo actuación ID: {})...", id);
        checkOverlap(dto, id);
        logger.debug("✓ No se encontraron solapamientos horarios");

        // Paso 3: Buscar y validar el nuevo artista
        Artist artist = artistRepository.findById(dto.getArtistId())
                .orElseThrow(() -> {
                    logger.error("Artista con ID {} no encontrado al actualizar actuación", dto.getArtistId());
                    return new AppException("msg.artist.not-found", dto.getArtistId());
                });
        logger.debug("Artista encontrado: {}", artist.getName());

        // Paso 4: Buscar y validar el nuevo escenario
        Stage stage = stageRepository.findById(dto.getStageId())
                .orElseThrow(() -> {
                    logger.error("Escenario con ID {} no encontrado al actualizar actuación", dto.getStageId());
                    return new AppException("msg.stage.not-found", dto.getStageId());
                });
        logger.debug("Escenario encontrado: {}", stage.getName());

        // Paso 5: Actualizar los campos de la entidad
        performanceMapper.updateEntityFromDTO(dto, existingPerformance);
        existingPerformance.setArtist(artist);
        existingPerformance.setStage(stage);
        logger.debug("Datos de la actuación actualizados en memoria");

        // Paso 6: Persistir cambios
        Performance updated = performanceRepository.save(existingPerformance);

        logger.info("✓ Actuación ID {} actualizada correctamente. Nuevo programa: '{}' en '{}', {} - {}",
                id,
                artist.getName(),
                stage.getName(),
                dto.getStartTime(),
                dto.getEndTime());

        return performanceMapper.toDTO(updated);
    }

    /**
     * Elimina una actuación del programa del festival.
     *
     * @param id ID de la actuación a eliminar
     * @throws AppException si la actuación no existe
     */
    @Transactional
    public void deletePerformance(Long id) {
        logger.info("Iniciando eliminación de actuación con ID: {}", id);

        // Verificar que la actuación existe antes de intentar eliminar
        if (!performanceRepository.existsById(id)) {
            logger.error("Intento de eliminar actuación inexistente con ID: {}", id);
            throw new AppException("msg.performance.not-found", id);
        }

        // Eliminar la actuación
        performanceRepository.deleteById(id);
        logger.info("✓ Actuación con ID {} eliminada correctamente del programa", id);
    }

    /**
     * Método privado de validación centralizada de solapamientos horarios.
     * Verifica que no existan actuaciones en el mismo escenario con horarios que se superpongan.
     * <p>
     * Lógica de solapamiento:
     * - Dos actuaciones se solapan si están en el mismo escenario Y
     * - El inicio de una es antes del fin de la otra Y
     * - El fin de una es después del inicio de la otra
     * <p>
     * Exclusión de actuación actual:
     * - En actualizaciones, se pasa currentId para excluir esa actuación de la búsqueda
     * - En creaciones, currentId es null (no hay nada que excluir)
     *
     * @param dto       DTO con el escenario y horarios a validar
     * @param currentId ID de la actuación actual (null en creaciones, ID en actualizaciones)
     * @throws AppException si se detecta solapamiento horario
     */
    private void checkOverlap(PerformanceCreateDTO dto, Long currentId) {
        logger.debug("Verificando solapamientos para Escenario ID: {}, Horario: {} - {}, Excluir ID: {}",
                dto.getStageId(),
                dto.getStartTime(),
                dto.getEndTime(),
                currentId != null ? currentId : "NINGUNO");

        // Consultar repositorio para verificar solapamientos
        boolean overlaps = performanceRepository.existsOverlappingPerformance(
                dto.getStageId(),
                dto.getStartTime(),
                dto.getEndTime(),
                currentId
        );

        if (overlaps) {
            // Se encontró un solapamiento horario
            logger.error("⚠ CONFLICTO HORARIO detectado. Escenario: {}, Horario solicitado: {} - {}",
                    dto.getStageId(), dto.getStartTime(), dto.getEndTime());

            // Lanzar excepción que será traducida por GlobalExceptionHandler
            throw new AppException("msg.validation.performance.overlap");
        } else {
            logger.trace("No se encontraron solapamientos horarios");
        }
    }
}