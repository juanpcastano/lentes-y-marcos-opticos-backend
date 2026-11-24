package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRun;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRunEvent;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventAction;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventEntity;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.IgnoredBrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.IgnoredSkuRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixSyncRunEventRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixSyncRunRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.Entry;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.ImportResult;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.NewSku;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.NewSkuState;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.SyncResult;

@ExtendWith(MockitoExtension.class)
class SoftixIntegrationServiceTest {
    private static final String LEVEL_INFO = "INFO";
    private static final String LEVEL_ERROR = "ERROR";

    @Mock
    private SoftixClient softixClient;
    @Mock
    private SoftixCatalogSyncService catalogSyncService;
    @Mock
    private SoftixSyncRunRepository runRepository;
    @Mock
    private SoftixSyncRunEventRepository eventRepository;
    @Mock
    private IgnoredBrandRepository ignoredBrandRepository;
    @Mock
    private IgnoredSkuRepository ignoredSkuRepository;
    @Mock
    private TransactionTemplate transactionTemplate;
    @InjectMocks
    private SoftixIntegrationService service;

    @BeforeEach
    void transaccionesEjecutanElCallback() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void sincronizacionExitosaQuedaRegistradaConEventosYConteos() {
        AtomicReference<SoftixSyncRun> saved = runRepositoryAnswer();
        when(catalogSyncService.sync()).thenReturn(new SyncResult(5, 1, 0, 3, 2, List.of(
                new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.UPDATED, "M1", "stock", "4", "7",
                        "Cambio de stock"),
                new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.RESTORED, "M2", "estado", "HIDDEN",
                        "INACTIVE", "Vuelve a aparecer en Softix"))));

        SoftixSyncRunDto run = service.runSync(SyncRunTrigger.MANUAL);

        assertThat(run.status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(run.trigger()).isEqualTo(SyncRunTrigger.MANUAL);
        assertThat(run.updated()).isEqualTo(5);
        assertThat(run.restored()).isEqualTo(1);
        assertThat(run.errors()).isZero();
        assertThat(saved.get().getRestoredCount()).isEqualTo(1);

        List<SoftixSyncRunEvent> events = eventosGuardados();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getEntityType()).isEqualTo(SyncEventEntity.PRODUCT);
        assertThat(events.get(0).getEntityKey()).isEqualTo("M1");
        assertThat(events.get(0).getField()).isEqualTo("stock");
        assertThat(events.get(0).getOldValue()).isEqualTo("4");
        assertThat(events.get(0).getNewValue()).isEqualTo("7");
        assertThat(events.get(2).getAction()).isEqualTo(SyncEventAction.SUMMARY);
        assertThat(events.get(2).getEntityType()).isEqualTo(SyncEventEntity.RUN);
        assertThat(events.get(2).getMessage()).contains("5 actualizados");
    }

    @Test
    void conErroresPorSkuLaEjecucionQuedaParcial() {
        runRepositoryAnswer();
        when(catalogSyncService.sync()).thenReturn(new SyncResult(3, 0, 1, 0, 0, List.of(
                new Entry(LEVEL_ERROR, SyncEventEntity.PRODUCT, SyncEventAction.BLOCKED, "M3", null, null, null,
                        "Sin referencia en Softix"))));

        SoftixSyncRunDto run = service.runSync(SyncRunTrigger.MANUAL);

        assertThat(run.status()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(run.errors()).isEqualTo(1);
    }

    @Test
    void fallaDeSoftixQuedaFallidaYRegistraElErrorSinAplicarEventos() {
        AtomicReference<SoftixSyncRun> saved = runRepositoryAnswer();
        when(catalogSyncService.sync()).thenThrow(new SoftixClientException("No fue posible comunicarse con Softix"));

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("No fue posible comunicarse con Softix");

        assertThat(saved.get().getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(saved.get().getErrorMessage()).isEqualTo("No fue posible comunicarse con Softix");
        ArgumentCaptor<SoftixSyncRunEvent> event = ArgumentCaptor.forClass(SoftixSyncRunEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getEntityType()).isEqualTo(SyncEventEntity.RUN);
        assertThat(event.getValue().getLevel()).isEqualTo(LEVEL_ERROR);
        verify(eventRepository, never()).saveAll(any());
    }

    @Test
    void errorInesperadoNoExponeSuMensaje() {
        runRepositoryAnswer();
        when(catalogSyncService.sync()).thenThrow(new IllegalStateException("token=abc123"));

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("Error inesperado durante la sincronización");
    }

    @Test
    void catalogoVacioDejaLaEjecucionFallida() {
        AtomicReference<SoftixSyncRun> saved = runRepositoryAnswer();
        when(catalogSyncService.sync()).thenThrow(new IllegalStateException("Softix devolvió un catálogo vacío"));

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("Softix devolvió un catálogo vacío");
        assertThat(saved.get().getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(saved.get().getErrorMessage()).contains("catálogo vacío");
    }

    @Test
    void noInicianDosSincronizacionesALaVez() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("Ya hay una sincronización en curso");

        verify(catalogSyncService, never()).sync();
        verify(eventRepository, never()).saveAll(any());
    }

    @Test
    void laEjecucionSeCreaAntesDeTocarProductos() {
        runRepositoryAnswer();
        when(catalogSyncService.importNewProducts(any(), any())).thenReturn(importado(List.of(), List.of(), List.of(),
                List.of()));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(0, 0, 0, 0, 0, List.of()));

        service.runImport();

        InOrder order = inOrder(runRepository, catalogSyncService);
        order.verify(runRepository).save(any(SoftixSyncRun.class));
        order.verify(catalogSyncService).importNewProducts(any(), any());
        order.verify(catalogSyncService).sync();
    }

    @Test
    void previewDeImportacionSeparaValidosBloqueadosEIgnorados() {
        when(ignoredBrandRepository.findAll()).thenReturn(List.of());
        when(ignoredSkuRepository.findAll()).thenReturn(List.of());
        when(catalogSyncService.preview()).thenReturn(new SyncResult(0, 0, 0, 0, 0, List.of()));
        var valid = new NewSku("M1", "NUEVA", "R1", "Negro", "CAT", "Válido", BigDecimal.TEN, 2,
                NewSkuState.VALID, null);
        var blocked = new NewSku("M2", "OTRA", "R2", "Negro", "CAT", "Bloqueado", BigDecimal.ZERO, 0,
                NewSkuState.BLOCKED, "Sin precio válido en Softix");
        var ignored = new NewSku("M3", "IGNORADA", "R3", "Negro", "CAT", "Ignorado", BigDecimal.TEN, 0,
                NewSkuState.IGNORED_BY_BRAND, null);
        when(catalogSyncService.previewImport(any(), any())).thenReturn(
                new SoftixCatalogSyncService.ImportPreview(List.of(valid, blocked, ignored),
                        List.of("NUEVA"), List.of("CAT")));

        var preview = service.importPreview();

        assertThat(preview.newCount()).isEqualTo(1);
        assertThat(preview.blockedCount()).isEqualTo(1);
        assertThat(preview.ignoredCount()).isEqualTo(1);
        assertThat(preview.errors()).isEqualTo(1);
        assertThat(preview.toCreate()).containsExactly("M1");
        assertThat(preview.withError()).containsExactly("M2");
        assertThat(preview.newBrands()).containsExactly("NUEVA");
        assertThat(preview.newSkus())
                .extracting(com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportNewSkuDto::sku,
                        com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportNewSkuDto::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("M1", "VALID"),
                        org.assertj.core.groups.Tuple.tuple("M2", "BLOCKED"),
                        org.assertj.core.groups.Tuple.tuple("M3", "IGNORED_BY_BRAND"));
    }

    @Test
    void importacionConBloqueadosImportablesQuedaParcialYLosRegistra() {
        runRepositoryAnswer();
        when(catalogSyncService.importNewProducts(any(), any())).thenReturn(importado(List.of(), List.of(), List.of(),
                List.of(new NewSku("M9", "RAY BAN", "R9", "Negro", "MONTURAS", "Bloqueado", BigDecimal.ZERO, 0,
                        NewSkuState.BLOCKED, "Sin precio válido en Softix"))));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(0, 0, 0, 0, 0, List.of()));

        SoftixSyncRunDto run = service.runImport();

        assertThat(run.status()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(run.errors()).isEqualTo(1);
        assertThat(eventosGuardados()).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(LEVEL_ERROR);
            assertThat(event.getAction()).isEqualTo(SyncEventAction.BLOCKED);
            assertThat(event.getEntityKey()).isEqualTo("M9");
            assertThat(event.getMessage()).contains("Sin precio");
        });
    }

    @Test
    void importacionConBloqueadoIgnoradoNoCuentaComoError() {
        runRepositoryAnswer();
        when(catalogSyncService.importNewProducts(any(), any())).thenReturn(importado(List.of(), List.of(), List.of(),
                List.of(new NewSku("M9", "RAY BAN", null, "Negro", "MONTURAS", "Ignorado", BigDecimal.TEN, 0,
                        NewSkuState.IGNORED_BY_BRAND, null))));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(0, 0, 0, 0, 0, List.of()));

        SoftixSyncRunDto run = service.runImport();

        assertThat(run.status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(run.errors()).isZero();
        assertThat(eventosGuardados()).noneSatisfy(event -> assertThat(event.getEntityKey()).isEqualTo("M9"));
    }

    @Test
    void importacionRegistraLaMarcaYCategoriaCreadasUnaSolaVez() {
        runRepositoryAnswer();
        when(catalogSyncService.importNewProducts(any(), any())).thenReturn(importado(List.of("M1", "M2"),
                List.of("NUEVA"), List.of("CAT"), List.of()));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(0, 0, 0, 0, 0, List.of()));

        SoftixSyncRunDto run = service.runImport();

        assertThat(run.created()).isEqualTo(2);
        assertThat(run.newBrands()).isEqualTo(1);
        assertThat(run.newCategories()).isEqualTo(1);
        assertThat(eventosGuardados())
                .filteredOn(e -> e.getEntityType() == SyncEventEntity.BRAND)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getAction()).isEqualTo(SyncEventAction.CREATED);
                    assertThat(e.getEntityKey()).isEqualTo("NUEVA");
                });
        assertThat(eventosGuardados())
                .filteredOn(e -> e.getEntityType() == SyncEventEntity.CATEGORY)
                .singleElement()
                .satisfies(e -> assertThat(e.getEntityKey()).isEqualTo("CAT"));
    }

    @Test
    void lasEjecucionesHuerfanasSeMarcanFallidasAlArrancar() {
        SoftixSyncRun huerfana = new SoftixSyncRun(SyncRunTrigger.MANUAL, LocalDateTime.now());
        huerfana.setId(UUID.randomUUID());
        when(runRepository.findByStatus(SyncRunStatus.RUNNING)).thenReturn(List.of(huerfana));

        service.failOrphanedRuns();

        assertThat(huerfana.getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(huerfana.getFinishedAt()).isNotNull();
        assertThat(huerfana.getErrorMessage()).contains("reinició");
        ArgumentCaptor<SoftixSyncRunEvent> event = ArgumentCaptor.forClass(SoftixSyncRunEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getRunId()).isEqualTo(huerfana.getId());
        assertThat(event.getValue().getLevel()).isEqualTo(LEVEL_ERROR);
    }

    private static ImportResult importado(List<String> skus, List<String> brands, List<String> categories,
            List<NewSku> newSkus) {
        return new ImportResult(skus, brands, categories, newSkus);
    }

    /** Guarda la ejecución como lo hace el repositorio: le asigna id y la devuelve. */
    private AtomicReference<SoftixSyncRun> runRepositoryAnswer() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(false);
        AtomicReference<SoftixSyncRun> saved = new AtomicReference<>();
        when(runRepository.save(any(SoftixSyncRun.class))).thenAnswer(invocation -> {
            SoftixSyncRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(UUID.randomUUID());
            }
            saved.set(run);
            return run;
        });
        when(runRepository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private List<SoftixSyncRunEvent> eventosGuardados() {
        ArgumentCaptor<List<SoftixSyncRunEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository, atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }
}
