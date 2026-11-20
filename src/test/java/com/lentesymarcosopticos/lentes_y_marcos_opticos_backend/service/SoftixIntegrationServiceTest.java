package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRun;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRunLog;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixSyncRunLogRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixSyncRunRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.Entry;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.SyncResult;

@ExtendWith(MockitoExtension.class)
class SoftixIntegrationServiceTest {
    @Mock
    private SoftixClient softixClient;
    @Mock
    private SoftixCatalogSyncService catalogSyncService;
    @Mock
    private SoftixSyncRunRepository runRepository;
    @Mock
    private SoftixSyncRunLogRepository logRepository;
    @InjectMocks
    private SoftixIntegrationService service;

    @Test
    void sincronizacionExitosaQuedaRegistradaConResumenYAvisos() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(false);
        when(runRepository.save(any(SoftixSyncRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(5, 1, 0, 3, 2,
                List.of(new Entry("INFO", "M1", "stock", "4", "7", "Cambio de stock"))));

        SoftixSyncRunDto run = service.runSync(SyncRunTrigger.MANUAL);

        assertThat(run.status()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(run.updated()).isEqualTo(5);
        assertThat(run.trigger()).isEqualTo(SyncRunTrigger.MANUAL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SoftixSyncRunLog>> logs = ArgumentCaptor.forClass(List.class);
        verify(logRepository).saveAll(logs.capture());
        assertThat(logs.getValue()).hasSize(2);
        assertThat(logs.getValue().get(0).getFieldName()).isEqualTo("stock");
        assertThat(logs.getValue().get(0).getOldValue()).isEqualTo("4");
        assertThat(logs.getValue().get(0).getNewValue()).isEqualTo("7");
        assertThat(logs.getValue().get(1).getMessage()).contains("5 actualizados");
    }

    @Test
    void conErroresPorSkuLaEjecucionQuedaParcial() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(false);
        when(runRepository.save(any(SoftixSyncRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogSyncService.sync()).thenReturn(new SyncResult(3, 0, 1, 0, 0, List.of()));

        SoftixSyncRunDto run = service.runSync(SyncRunTrigger.MANUAL);

        assertThat(run.status()).isEqualTo(SyncRunStatus.PARTIAL);
        assertThat(run.errors()).isEqualTo(1);
    }

    @Test
    void fallaDeSoftixQuedaRegistradaComoFallidaSinDetallesInternos() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(false);
        when(runRepository.save(any(SoftixSyncRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogSyncService.sync()).thenThrow(new SoftixClientException("No fue posible comunicarse con Softix"));

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("No fue posible comunicarse con Softix");

        ArgumentCaptor<SoftixSyncRun> saved = ArgumentCaptor.forClass(SoftixSyncRun.class);
        verify(runRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        SoftixSyncRun last = saved.getAllValues().get(saved.getAllValues().size() - 1);
        assertThat(last.getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(last.getErrorMessage()).isEqualTo("No fue posible comunicarse con Softix");
        verify(logRepository).save(any(SoftixSyncRunLog.class));
    }

    @Test
    void errorInesperadoNoExponeSuMensaje() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(false);
        when(runRepository.save(any(SoftixSyncRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(catalogSyncService.sync()).thenThrow(new IllegalStateException("token=abc123"));

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("Error inesperado durante la sincronización");
    }

    @Test
    void noInicianDosSincronizacionesALaVez() {
        when(runRepository.existsByStatus(SyncRunStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> service.runSync(SyncRunTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessage("Ya hay una sincronización en curso");

        verify(catalogSyncService, never()).sync();
        verify(logRepository, never()).saveAll(anyList());
    }
}
