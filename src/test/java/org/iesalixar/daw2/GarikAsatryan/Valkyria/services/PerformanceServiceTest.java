package org.iesalixar.daw2.GarikAsatryan.Valkyria.services;

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
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.PerformanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock private PerformanceRepository performanceRepository;
    @Mock private ArtistRepository artistRepository;
    @Mock private StageRepository stageRepository;
    @Mock private PerformanceMapper performanceMapper;
    @Mock private PaginationComponent paginationComponent;

    @InjectMocks
    private PerformanceService performanceService;

    // ─── helpers ───────────────────────────────────────────────────────────────

    private PerformanceCreateDTO makeDTO(Long artistId, Long stageId) {
        PerformanceCreateDTO dto = new PerformanceCreateDTO();
        dto.setArtistId(artistId);
        dto.setStageId(stageId);
        dto.setStartTime(LocalDateTime.of(2025, 7, 10, 20, 0));
        dto.setEndTime(LocalDateTime.of(2025, 7, 10, 22, 0));
        return dto;
    }

    private Artist makeArtist(Long id) {
        Artist a = new Artist();
        a.setId(id);
        a.setName("Artista Test");
        return a;
    }

    private Stage makeStage(Long id) {
        Stage s = new Stage();
        s.setId(id);
        s.setName("Escenario Principal");
        return s;
    }

    // ─── createPerformance ────────────────────────────────────────────────────

    @Test
    void createPerformance_validData_savesAndReturnsDTO() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);
        Artist artist = makeArtist(1L);
        Stage stage = makeStage(2L);
        Performance perf = new Performance();
        PerformanceDTO perfDTO = new PerformanceDTO();

        when(performanceRepository.existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), null))
                .thenReturn(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(stageRepository.findById(2L)).thenReturn(Optional.of(stage));
        when(performanceMapper.toEntity(dto)).thenReturn(perf);
        when(performanceRepository.save(perf)).thenReturn(perf);
        when(performanceMapper.toDTO(perf)).thenReturn(perfDTO);

        PerformanceDTO result = performanceService.createPerformance(dto);

        assertThat(result).isEqualTo(perfDTO);
        assertThat(perf.getArtist()).isEqualTo(artist);
        assertThat(perf.getStage()).isEqualTo(stage);
        verify(performanceRepository).save(perf);
    }

    @Test
    void createPerformance_overlappingTime_throwsAppException() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);

        when(performanceRepository.existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), null))
                .thenReturn(true);

        assertThatThrownBy(() -> performanceService.createPerformance(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.validation.performance.overlap");

        verify(performanceRepository, never()).save(any());
        verify(artistRepository, never()).findById(any());
    }

    @Test
    void createPerformance_artistNotFound_throwsAppException() {
        PerformanceCreateDTO dto = makeDTO(99L, 2L);

        when(performanceRepository.existsOverlappingPerformance(eq(2L), any(), any(), isNull()))
                .thenReturn(false);
        when(artistRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.createPerformance(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.artist.not-found");

        verify(performanceRepository, never()).save(any());
    }

    @Test
    void createPerformance_stageNotFound_throwsAppException() {
        PerformanceCreateDTO dto = makeDTO(1L, 99L);

        when(performanceRepository.existsOverlappingPerformance(eq(99L), any(), any(), isNull()))
                .thenReturn(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(makeArtist(1L)));
        when(stageRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.createPerformance(dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.stage.not-found");

        verify(performanceRepository, never()).save(any());
    }

    @Test
    void createPerformance_passesNullAsCurrentIdToOverlapCheck() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);

        when(performanceRepository.existsOverlappingPerformance(eq(2L), any(), any(), isNull()))
                .thenReturn(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(makeArtist(1L)));
        when(stageRepository.findById(2L)).thenReturn(Optional.of(makeStage(2L)));
        when(performanceMapper.toEntity(dto)).thenReturn(new Performance());
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceMapper.toDTO(any())).thenReturn(new PerformanceDTO());

        performanceService.createPerformance(dto);

        // null means "no ID to exclude" — creation case
        verify(performanceRepository).existsOverlappingPerformance(eq(2L), any(), any(), isNull());
    }

    // ─── updatePerformance ────────────────────────────────────────────────────

    @Test
    void updatePerformance_validData_updatesRelationsAndPersists() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);
        Artist artist = makeArtist(1L);
        Stage stage = makeStage(2L);
        Performance existing = new Performance();
        existing.setArtist(artist);
        existing.setStage(stage);
        PerformanceDTO perfDTO = new PerformanceDTO();

        when(performanceRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(performanceRepository.existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), 5L))
                .thenReturn(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(stageRepository.findById(2L)).thenReturn(Optional.of(stage));
        when(performanceRepository.save(existing)).thenReturn(existing);
        when(performanceMapper.toDTO(existing)).thenReturn(perfDTO);

        PerformanceDTO result = performanceService.updatePerformance(5L, dto);

        assertThat(result).isEqualTo(perfDTO);
        verify(performanceMapper).updateEntityFromDTO(dto, existing);
        verify(performanceRepository).save(existing);
    }

    @Test
    void updatePerformance_notFound_throwsAppException() {
        when(performanceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.updatePerformance(42L, makeDTO(1L, 2L)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.performance.not-found");
    }

    @Test
    void updatePerformance_passesCurrentIdToOverlapCheck() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);
        Artist artist = makeArtist(1L);
        Stage stage = makeStage(2L);
        Performance existing = new Performance();
        existing.setArtist(artist);
        existing.setStage(stage);

        when(performanceRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(performanceRepository.existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), 7L))
                .thenReturn(false);
        when(artistRepository.findById(1L)).thenReturn(Optional.of(artist));
        when(stageRepository.findById(2L)).thenReturn(Optional.of(stage));
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceMapper.toDTO(any())).thenReturn(new PerformanceDTO());

        performanceService.updatePerformance(7L, dto);

        // ID 7L must be passed so the query excludes the current performance from the overlap search
        verify(performanceRepository).existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), 7L);
        verify(performanceRepository, never()).existsOverlappingPerformance(any(), any(), any(), isNull());
    }

    @Test
    void updatePerformance_newTimeOverlaps_throwsAppException() {
        PerformanceCreateDTO dto = makeDTO(1L, 2L);
        Artist artist = makeArtist(1L);
        Stage stage = makeStage(2L);
        Performance existing = new Performance();
        existing.setArtist(artist);
        existing.setStage(stage);

        when(performanceRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(performanceRepository.existsOverlappingPerformance(2L, dto.getStartTime(), dto.getEndTime(), 3L))
                .thenReturn(true);

        assertThatThrownBy(() -> performanceService.updatePerformance(3L, dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.validation.performance.overlap");

        verify(performanceRepository, never()).save(any());
    }

    @Test
    void updatePerformance_artistNotFound_throwsAppException() {
        PerformanceCreateDTO dto = makeDTO(99L, 2L);
        Artist artist = makeArtist(1L);
        Stage stage = makeStage(2L);
        Performance existing = new Performance();
        existing.setArtist(artist);
        existing.setStage(stage);

        when(performanceRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(performanceRepository.existsOverlappingPerformance(eq(2L), any(), any(), eq(1L))).thenReturn(false);
        when(artistRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.updatePerformance(1L, dto))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.artist.not-found");
    }

    // ─── deletePerformance ────────────────────────────────────────────────────

    @Test
    void deletePerformance_exists_deletesSuccessfully() {
        when(performanceRepository.existsById(1L)).thenReturn(true);

        assertThatNoException().isThrownBy(() -> performanceService.deletePerformance(1L));
        verify(performanceRepository).deleteById(1L);
    }

    @Test
    void deletePerformance_notFound_throwsAppException() {
        when(performanceRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> performanceService.deletePerformance(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.performance.not-found");

        verify(performanceRepository, never()).deleteById(any());
    }

    // ─── getPerformanceById ───────────────────────────────────────────────────

    @Test
    void getPerformanceById_found_returnsDTO() {
        Performance perf = new Performance();
        PerformanceDTO dto = new PerformanceDTO();
        when(performanceRepository.findById(1L)).thenReturn(Optional.of(perf));
        when(performanceMapper.toDTO(perf)).thenReturn(dto);

        assertThat(performanceService.getPerformanceById(1L)).isEqualTo(dto);
    }

    @Test
    void getPerformanceById_notFound_throwsAppException() {
        when(performanceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.getPerformanceById(99L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("msg.performance.not-found");
    }

    // ─── getAllPerformances (paginado) ─────────────────────────────────────────

    @Test
    void getAllPerformances_withoutSearch_callsFindAll() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        Pageable pageable = PageRequest.of(0, 10);
        Performance perf = new Performance();
        Page<Performance> page = new PageImpl<>(List.of(perf));

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(performanceRepository.findAll(pageable)).thenReturn(page);
        when(performanceMapper.toDTO(perf)).thenReturn(new PerformanceDTO());

        List<PerformanceDTO> result = performanceService.getAllPerformances(filter);

        assertThat(result).hasSize(1);
        verify(performanceRepository).findAll(pageable);
        verify(performanceRepository, never()).searchPerformances(any(), any());
    }

    @Test
    void getAllPerformances_withSearch_callsSearchPerformances() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        filter.setSearch("Metallica");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Performance> page = new PageImpl<>(List.of());

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(performanceRepository.searchPerformances("Metallica", pageable)).thenReturn(page);

        List<PerformanceDTO> result = performanceService.getAllPerformances(filter);

        assertThat(result).isEmpty();
        verify(performanceRepository).searchPerformances("Metallica", pageable);
        verify(performanceRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getAllPerformances_emptyBlankSearch_callsFindAll() {
        FilterDTO filter = new FilterDTO();
        filter.setPage(0);
        filter.setItemsPerPage(10);
        filter.setSearch("   "); // blank, should be treated as no search
        Pageable pageable = PageRequest.of(0, 10);
        Page<Performance> page = new PageImpl<>(List.of());

        when(paginationComponent.createPageable(filter, "id")).thenReturn(pageable);
        when(performanceRepository.findAll(pageable)).thenReturn(page);

        performanceService.getAllPerformances(filter);

        verify(performanceRepository).findAll(pageable);
        verify(performanceRepository, never()).searchPerformances(any(), any());
    }
}
