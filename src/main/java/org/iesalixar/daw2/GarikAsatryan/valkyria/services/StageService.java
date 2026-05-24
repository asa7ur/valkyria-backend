package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.StageCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.StageDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Stage;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.StageMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.StageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de escenarios (stages) del festival.
 * Los escenarios son los espacios físicos donde tienen lugar las actuaciones.
 */
@Service
@RequiredArgsConstructor
public class StageService {
    private static final Logger logger = LoggerFactory.getLogger(StageService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final StageRepository stageRepository;
    private final StageMapper stageMapper;
    private final PaginationComponent paginationComponent;

    /**
     * Obtiene escenarios paginados con búsqueda opcional.
     * Permite filtrar por nombre o ubicación del escenario.
     */
    @Transactional(readOnly = true)
    public List<StageDTO> getAllStages(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de escenarios. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch().replaceAll("[\r\n]", "_") : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        // Decisión: búsqueda filtrada o listado completo
        Page<Stage> stagePage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? stageRepository.searchStages(filterDTO.getSearch(), pageable)
                : stageRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, stagePage);

        logger.debug("Escenarios encontrados: {} de {} totales",
                stagePage.getNumberOfElements(),
                stagePage.getTotalElements());

        // Convertir entidades a DTOs usando el mapper
        return stagePage.getContent().stream()
                .map(stageMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un escenario específico por su ID.
     * Incluye toda la información del escenario (nombre, capacidad, ubicación, etc.).
     */
    @Transactional(readOnly = true)
    public StageDTO getStageById(Long id) {
        logger.info("Buscando detalle del escenario con ID: {}", id);

        return stageRepository.findById(id)
                .map(stageMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.stage.not-found", id));
    }

    /**
     * Crea un nuevo escenario en el sistema.
     * El escenario estará disponible para programar actuaciones inmediatamente.
     */
    @Transactional
    public StageDTO createStage(StageCreateDTO dto) {
        logger.info("Iniciando creación de escenario: {}", dto.getName().replaceAll("[\r\n]", "_"));

        // Conversión de DTO a entidad
        Stage stage = stageMapper.toEntity(dto);

        // Persistencia en base de datos
        Stage saved = stageRepository.save(stage);

        logger.info("✓ Escenario creado con éxito. ID: {}, Nombre: '{}', Capacidad: {}",
                saved.getId(),
                saved.getName().replaceAll("[\r\n]", "_"),
                saved.getCapacity());

        return stageMapper.toDTO(saved);
    }

    /**
     * Actualiza los datos de un escenario existente.
     * IMPORTANTE: Si hay actuaciones programadas en este escenario, se actualizarán
     * automáticamente para reflejar el nuevo nombre/información del escenario.
     */
    @Transactional
    public StageDTO updateStage(Long id, StageCreateDTO dto) {
        logger.info("Iniciando actualización del escenario con ID: {}", id);

        // Buscar el escenario existente
        Stage existingStage = stageRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Escenario con ID {} no encontrado para actualización", id);
                    return new AppException("msg.stage.not-found", id);
                });

        logger.debug("Escenario encontrado: {}. Datos actuales: Nombre={}, Capacidad={}",
                id,
                existingStage.getName(),
                existingStage.getCapacity());

        // Actualizar los campos de la entidad con los datos del DTO
        stageMapper.updateEntityFromDTO(dto, existingStage);
        logger.debug("Datos del escenario actualizados en memoria");

        // Guardar cambios
        Stage updatedStage = stageRepository.save(existingStage);

        logger.info("✓ Escenario con ID {} actualizado correctamente. Nuevo nombre: '{}', Nueva capacidad: {}",
                id,
                updatedStage.getName(),
                updatedStage.getCapacity());

        return stageMapper.toDTO(updatedStage);
    }

    /**
     * Elimina un escenario del sistema.
     * <p>
     * RESTRICCIONES IMPORTANTES:
     * - No se puede eliminar si tiene actuaciones programadas (FK constraint)
     * - No se puede eliminar si tiene patrocinadores asociados (M2M relationship)
     * <p>
     * En estos casos, la base de datos rechazará la operación lanzando
     * DataIntegrityViolationException, que debe ser manejada por el controlador.
     * <p>
     * Estrategia recomendada:
     * - Primero eliminar/reasignar todas las actuaciones del escenario
     * - Luego eliminar las asociaciones con patrocinadores
     * - Finalmente eliminar el escenario
     *
     * @param id ID del escenario a eliminar
     * @throws AppException                                            si el escenario no existe
     * @throws org.springframework.dao.DataIntegrityViolationException si hay relaciones activas
     */
    @Transactional
    public void deleteStage(Long id) {
        logger.info("Iniciando eliminación del escenario con ID: {}", id);

        // Verificar que el escenario existe antes de intentar eliminar
        if (!stageRepository.existsById(id)) {
            logger.error("Intento de eliminar escenario inexistente con ID: {}", id);
            throw new AppException("msg.stage.not-found", id);
        }

        logger.debug("Escenario encontrado, procediendo a eliminar");
        logger.warn("ADVERTENCIA: Si el escenario tiene actuaciones o patrocinadores asociados, " +
                "la eliminación fallará por restricción de integridad referencial");

        // Intentar eliminar el escenario
        // Nota: Si hay relaciones activas, esto lanzará DataIntegrityViolationException
        stageRepository.deleteById(id);

        logger.info("✓ Escenario con ID {} eliminado correctamente del sistema", id);
    }
}