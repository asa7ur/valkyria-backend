package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.components.PaginationComponent;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.FilterDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketTypeCreateDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.TicketTypeDTO;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.exceptions.AppException;
import org.iesalixar.daw2.GarikAsatryan.valkyria.mappers.TicketTypeMapper;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.TicketTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketTypeService {
    private static final Logger logger = LoggerFactory.getLogger(TicketTypeService.class);

    private final TicketTypeRepository ticketTypeRepository;
    private final TicketTypeMapper ticketTypeMapper;
    private final PaginationComponent paginationComponent;

    @Transactional(readOnly = true)
    public List<TicketTypeDTO> getAllTicketTypes(FilterDTO filterDTO) {
        logger.info("Iniciando búsqueda de entradas. Término: '{}', Página: {}, Tamaño: {}",
                filterDTO.getSearch() != null ? filterDTO.getSearch() : "SIN FILTRO",
                filterDTO.getPage(),
                filterDTO.getItemsPerPage());

        Pageable pageable = paginationComponent.createPageable(filterDTO, "id");

        Page<TicketType> ticketTypePage = (filterDTO.getSearch() != null && !filterDTO.getSearch().isBlank())
                ? ticketTypeRepository.searchTicketTypes(filterDTO.getSearch(), pageable)
                : ticketTypeRepository.findAll(pageable);

        paginationComponent.updateFilterMetadata(filterDTO, ticketTypePage);

        logger.debug("Entradas encontradas: {} de {} totales",
                ticketTypePage.getNumberOfElements(),
                ticketTypePage.getTotalElements());

        return ticketTypePage.getContent().stream()
                .map(ticketTypeMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TicketTypeDTO getTicketTypeById(Long id) {
        logger.info("Buscando tipo de entrada con ID: {}", id);

        TicketTypeDTO result = ticketTypeRepository.findById(id)
                .map(ticketTypeMapper::toDTO)
                .orElseThrow(() -> {
                    logger.error("Tipo de entrada con ID {} no encontrado", id);
                    return new AppException("msg.ticket.not-found", id);
                });

        logger.debug("Tipo de entrada encontrado: {}", result.getName());
        return result;
    }

    @Transactional
    public TicketTypeDTO createTicketType(TicketTypeCreateDTO dto) {
        TicketType entity = ticketTypeMapper.toEntity(dto);
        return ticketTypeMapper.toDTO(ticketTypeRepository.save(entity));
    }

    @Transactional
    public TicketTypeDTO updateTicketType(Long id, TicketTypeCreateDTO dto) {
        TicketType existing = ticketTypeRepository.findById(id)
                .orElseThrow(() -> new AppException("msg.ticket.not-found", id));

        ticketTypeMapper.updateEntityFromDTO(dto, existing);
        return ticketTypeMapper.toDTO(ticketTypeRepository.save(existing));
    }

    @Transactional
    public void deleteTicketType(Long id) {
        if (!ticketTypeRepository.existsById(id)) {
            throw new AppException("msg.ticket.not-found", id);
        }
        ticketTypeRepository.deleteById(id);
    }
}