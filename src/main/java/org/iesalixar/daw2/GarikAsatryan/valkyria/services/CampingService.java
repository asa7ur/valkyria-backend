package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.CampingDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Camping;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.CampingMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.CampingRepository;
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
 * Servicio de negocio para la gestión de CAMPINGS.
 * Proporciona operaciones CRUD completas con validación de tipos de camping.
 */
@Service
@RequiredArgsConstructor
public class CampingService {
    private static final Logger logger = LoggerFactory.getLogger(CampingService.class);

    // Inyección de dependencias mediante constructor (Lombok @RequiredArgsConstructor)
    private final CampingRepository campingRepository;
    private final CampingTypeRepository campingTypeRepository;
    private final CampingMapper campingMapper;
    private final PaginationComponent paginationComponent;

    /**
     * Obtiene una lista de artistas basada en filtros.
     * Actualiza el FilterDTO con los metadatos de paginación.
     */
    public List<CampingDTO> getAllCampings(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de campings. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<Camping> campingPage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? campingRepository.searchCampings(filterDTO.getSearch(), pageable)
                : campingRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, campingPage);

        logger.debug("Campings encontrados: {} de {} totales",
                campingPage.getNumberOfElements(),
                campingPage.getTotalElements());

        return campingPage.getContent().stream()
                .map(campingMapper::toDTO)
                .collect(Collectors.toList());
    }

    public CampingDTO getCampingById(Long id) {
        return campingRepository.findById(id)
                .map(campingMapper::toDTO)
                .orElseThrow(() -> new AppException("msg.camping.not-found", id));
    }

    @Transactional
    public CampingDTO createCamping(CampingCreateDTO dto) {
        logger.info("Iniciando creación manual de entrada de camping para: {} {}",
                dto.getFirstName(), dto.getLastName());
        logger.debug("Tipo de camping solicitado ID: {}", dto.getCampingTypeId());
        CampingType type = campingTypeRepository.findById(dto.getCampingTypeId())
                .orElseThrow(() -> new AppException("msg.camping.type-not-found", dto.getCampingTypeId()));

        logger.debug("Tipo de camping encontrado: {} (Precio: {}, Stock: {})",
                type.getName(), type.getPrice(), type.getStockAvailable());

        Camping camping = campingMapper.toEntity(dto);

        camping.setCampingType(type);

        Camping savedCamping = campingRepository.save(camping);

        logger.info("✓ Entrada Camping creada exitosamente. ID: {}, Asistente: {} {}, Tipo: {}",
                savedCamping.getId(),
                savedCamping.getFirstName(),
                savedCamping.getLastName(),
                type.getName());

        return campingMapper.toDTO(savedCamping);
    }

    @Transactional
    public CampingDTO updateCamping(Long id, CampingCreateDTO dto) {
        Camping existing = campingRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.camping.not-found", id));

        CampingType type = campingTypeRepository.findById(dto.getCampingTypeId())
                .orElseThrow(() -> new AppException("msg.camping.type-not-found", dto.getCampingTypeId()));

        campingMapper.updateEntityFromDTO(dto, existing);
        existing.setCampingType(type);

        Camping updatedCamping = campingRepository.save(existing);
        return campingMapper.toDTO(updatedCamping);
    }

    @Transactional
    public void deleteCamping(Long id) {
        logger.info("Iniciando eliminación de entrada al camping con ID: {}", id);

        // 1. Buscar el ticket para obtener su tipo antes de borrar
        Camping camping = campingRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.camping.not-found", id));

        // 2. Recuperar el tipo y devolver el stock
        CampingType type = camping.getCampingType();
        if (type != null) {
            type.setStockAvailable(type.getStockAvailable() + 1);
            campingTypeRepository.save(type);
            logger.info("Stock devuelto para camping tipo '{}'. Nuevo stock: {}",
                    type.getName(), type.getStockAvailable());
        }

        campingRepository.delete(camping);
        logger.info("✓ Entrada al camping con ID {} eliminada", id);
    }
}