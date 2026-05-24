package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingTypeCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingTypeDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.CampingTypeMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la gestión de tipos de camping.
 * Proporciona operaciones CRUD completas para los tipos de camping disponibles en el sistema.
 * Los tipos de camping son utilizados como categorías para clasificar los diferentes campings.
 */
@Service
@RequiredArgsConstructor
public class CampingTypeService {
    private static final Logger logger = LoggerFactory.getLogger(CampingTypeService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final CampingTypeRepository campingTypeRepository;
    private final CampingTypeMapper campingTypeMapper;
    private final PaginationComponent paginationComponent;

    public List<CampingTypeDTO> getAllCampingTypes(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de entradas. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<CampingType> campingTypePage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? campingTypeRepository.searchCampingTypes(filterDTO.getSearch(), pageable)
                : campingTypeRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, campingTypePage);

        logger.debug("Entradas encontradas: {} de {} totales",
                campingTypePage.getNumberOfElements(),
                campingTypePage.getTotalElements());

        return campingTypePage.getContent().stream()
                .map(campingTypeMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene un tipo de camping específico por su ID.
     *
     * @param id ID del tipo de camping
     * @return DTO del tipo de camping encontrado
     * @throws AppException si el tipo de camping no existe
     */
    public CampingTypeDTO getCampingTypeById(Long id) {
        logger.info("Buscando tipo de camping con ID: {}", id);

        CampingTypeDTO result = campingTypeRepository.findById(id)
                .map(campingTypeMapper::toDTO)
                .orElseThrow(() -> {
                    logger.error("Tipo de camping con ID {} no encontrado", id);
                    return new AppException("msg.camping.not-found", id);
                });

        logger.debug("Tipo de camping encontrado: {}", result.getName());
        return result;
    }

    /**
     * Crea un nuevo tipo de camping en el sistema.
     *
     * @param dto DTO con los datos del tipo de camping a crear
     * @return DTO del tipo de camping creado
     */
    @Transactional
    public CampingTypeDTO createCampingType(CampingTypeCreateDTO dto) {
        logger.info("Iniciando creación de nuevo tipo de camping: {}", dto.getName());

        // Conversión de DTO a entidad
        CampingType entity = campingTypeMapper.toEntity(dto);
        logger.debug("Entidad CampingType mapeada desde DTO");

        // Persistencia en base de datos
        CampingType savedEntity = campingTypeRepository.save(entity);
        logger.info("Tipo de camping creado con éxito. ID: {}, Nombre: {}",
                savedEntity.getId(), savedEntity.getName());

        return campingTypeMapper.toDTO(savedEntity);
    }

    /**
     * Actualiza los datos de un tipo de camping existente.
     * IMPORTANTE: Esta actualización afectará a todos los campings que utilicen este tipo.
     *
     * @param id  ID del tipo de camping a actualizar
     * @param dto DTO con los nuevos datos
     * @return DTO del tipo de camping actualizado
     * @throws AppException si el tipo de camping no existe
     */
    @Transactional
    public CampingTypeDTO updateCampingType(Long id, CampingTypeCreateDTO dto) {
        logger.info("Iniciando actualización del tipo de camping con ID: {}", id);

        // Buscar el tipo de camping existente
        CampingType existing = campingTypeRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("Tipo de camping con ID {} no encontrado para actualización", id);
                    return new AppException("msg.camping.not-found", id);
                });

        logger.debug("Tipo de camping encontrado: {}. Datos actuales: Nombre={}",
                id, existing.getName());

        // Actualizar los campos de la entidad con los datos del DTO
        campingTypeMapper.updateEntityFromDTO(dto, existing);
        logger.debug("Datos del tipo de camping actualizados en memoria");

        // Guardar cambios
        CampingType updatedEntity = campingTypeRepository.save(existing);
        logger.info("Tipo de camping con ID {} actualizado correctamente. Nuevo nombre: {}",
                id, updatedEntity.getName());

        return campingTypeMapper.toDTO(updatedEntity);
    }

    /**
     * Elimina un tipo de camping del sistema.
     * IMPORTANTE: Solo se puede eliminar si no hay campings asociados a este tipo.
     * Si hay campings usando este tipo, la base de datos rechazará la operación por restricción FK.
     *
     * @param id ID del tipo de camping a eliminar
     * @throws AppException                                            si el tipo de camping no existe
     * @throws org.springframework.dao.DataIntegrityViolationException si hay campings asociados
     */
    @Transactional
    public void deleteCampingType(Long id) {
        logger.info("Iniciando eliminación del tipo de camping con ID: {}", id);

        // Verificar que el tipo de camping existe antes de intentar eliminar
        if (!campingTypeRepository.existsById(id)) {
            logger.error("Intento de eliminar tipo de camping inexistente con ID: {}", id);
            throw new AppException("msg.camping.not-found", id);
        }

        logger.debug("Tipo de camping encontrado, procediendo a eliminar");

        // Eliminar el tipo de camping
        // Nota: Si hay campings asociados, se lanzará DataIntegrityViolationException
        campingTypeRepository.deleteById(id);
        logger.info("Tipo de camping con ID {} eliminado correctamente del sistema", id);
    }
}