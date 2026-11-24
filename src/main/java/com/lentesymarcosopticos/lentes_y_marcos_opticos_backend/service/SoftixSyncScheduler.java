package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Ejecuta la sincronización automática de Softix una vez por hora. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SoftixSyncScheduler {

    private final SoftixIntegrationService integrationService;

    @Scheduled(cron = "${softix.sync.cron:0 0 * * * *}")
    public void synchronizeHourly() {
        try {
            integrationService.runSync(SyncRunTrigger.SCHEDULED);
        } catch (RuntimeException ex) {
            log.warn("Sincronización horaria de Softix fallida: {}", ex.getMessage());
        }
    }
}
