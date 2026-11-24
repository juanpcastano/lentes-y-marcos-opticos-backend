package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;

@ExtendWith(MockitoExtension.class)
class SoftixSyncSchedulerTest {

    @Mock
    private SoftixIntegrationService integrationService;

    @Test
    void ejecutaSincronizacionProgramada() {
        new SoftixSyncScheduler(integrationService).synchronizeHourly();

        verify(integrationService).runSync(SyncRunTrigger.SCHEDULED);
    }

    @Test
    void unaFallaDelSchedulerNoSePropaga() {
        when(integrationService.runSync(SyncRunTrigger.SCHEDULED))
                .thenThrow(new RuntimeException("Softix caído"));

        new SoftixSyncScheduler(integrationService).synchronizeHourly();

        verify(integrationService).runSync(SyncRunTrigger.SCHEDULED);
    }
}
