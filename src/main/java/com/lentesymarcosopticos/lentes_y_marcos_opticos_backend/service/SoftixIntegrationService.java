package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixConnectionStatusDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncIssueDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncLogDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDetailDto;
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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SoftixIntegrationService — orquesta la integración con Softix desde el panel:
 * verificación de conexión, vista previa, ejecución y registro de cada sincronización.
 */
@Service
@Slf4j
@AllArgsConstructor
public class SoftixIntegrationService {
    private static final String PRODUCT_TYPE = "MONTURA";
    private static final String UNEXPECTED_ERROR = "Error inesperado durante la sincronización";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOG_RETENTION_DAYS = 90;

    private final SoftixClient softixClient;
    private final SoftixCatalogSyncService catalogSyncService;
    private final SoftixSyncRunRepository runRepository;
    private final SoftixSyncRunLogRepository logRepository;

    public SoftixConnectionStatusDto checkConnection() {
        LocalDateTime now = LocalDateTime.now();
        try {
            softixClient.listProducts(PRODUCT_TYPE, 1, 1);
            return new SoftixConnectionStatusDto(true, "Conexión correcta con Softix", now);
        } catch (SoftixClientException ex) {
            return new SoftixConnectionStatusDto(false, ex.getMessage(), now);
        }
    }

    public SoftixSyncPreviewDto preview() {
        SyncResult result = catalogSyncService.preview();
        return new SoftixSyncPreviewDto(
                result.updated(), result.hidden(), result.errors(),
                result.stockChanged(), result.priceChanged(),
                result.entries().stream().map(this::toIssueDto).toList());
    }

    /**
     * Ejecuta y registra una sincronización. Si Softix falla, el registro queda como FAILED
     * y se responde con error sin aplicar cambios parciales.
     */
    public synchronized SoftixSyncRunDto runSync(SyncRunTrigger trigger) {
        if (runRepository.existsByStatus(SyncRunStatus.RUNNING)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay una sincronización en curso");
        }
        logRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(LOG_RETENTION_DAYS));
        SoftixSyncRun run = runRepository.save(new SoftixSyncRun(trigger, LocalDateTime.now()));
        try {
            SyncResult result = catalogSyncService.sync();
            run.setUpdatedCount(result.updated());
            run.setHiddenCount(result.hidden());
            run.setErrorCount(result.errors());
            run.setStatus(result.errors() > 0 ? SyncRunStatus.PARTIAL : SyncRunStatus.SUCCESS);
            run.setFinishedAt(LocalDateTime.now());
            runRepository.save(run);

            List<SoftixSyncRunLog> logs = new ArrayList<>();
            for (Entry entry : result.entries()) {
                logs.add(new SoftixSyncRunLog(run.getId(), entry.level(), entry.sku(), entry.field(),
                        entry.oldValue(), entry.newValue(), entry.message()));
            }
            logs.add(new SoftixSyncRunLog(run.getId(), SoftixCatalogSyncService.LEVEL_INFO, null, String.format(
                    "Terminada: %d actualizados, %d ocultos, %d con error, "
                            + "%d cambios de stock, %d cambios de precio",
                    result.updated(), result.hidden(), result.errors(),
                    result.stockChanged(), result.priceChanged())));
            logRepository.saveAll(logs);
            return toDto(run);
        } catch (RuntimeException ex) {
            String message = safeMessage(ex);
            log.warn("Sincronización Softix fallida: {}", message);
            run.setStatus(SyncRunStatus.FAILED);
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(message);
            runRepository.save(run);
            logRepository.save(new SoftixSyncRunLog(run.getId(), "ERROR", null, message));
            throw new ApiException(HttpStatus.BAD_GATEWAY, message);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<SoftixSyncRunDto> listRuns(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var result = runRepository.findAllByOrderByStartedAtDesc(PageRequest.of(Math.max(page, 0), safeSize));
        return new PageResponse<>(
                result.map(this::toDto).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SoftixSyncRunDetailDto getRun(UUID id) {
        SoftixSyncRun run = runRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sincronización no encontrada"));
        List<SoftixSyncLogDto> logs = logRepository.findByRunIdOrderByIdAsc(id).stream()
                .map(this::toLogDto)
                .toList();
        return new SoftixSyncRunDetailDto(toDto(run), logs);
    }

    private static String safeMessage(RuntimeException ex) {
        if (ex instanceof SoftixClientException || ex instanceof ApiException) {
            return ex.getMessage();
        }
        return UNEXPECTED_ERROR;
    }

    private SoftixSyncRunDto toDto(SoftixSyncRun run) {
        return new SoftixSyncRunDto(
                run.getId(),
                run.getTriggerType(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getUpdatedCount(),
                run.getHiddenCount(),
                run.getErrorCount(),
                run.getErrorMessage());
    }

    private SoftixSyncLogDto toLogDto(SoftixSyncRunLog entry) {
        return new SoftixSyncLogDto(entry.getId(), entry.getLevel(), entry.getSku(), entry.getFieldName(),
                entry.getOldValue(), entry.getNewValue(), entry.getMessage(), entry.getCreatedAt());
    }

    private SoftixSyncIssueDto toIssueDto(Entry entry) {
        return new SoftixSyncIssueDto(entry.level(), entry.sku(), entry.field(), entry.oldValue(),
                entry.newValue(), entry.message());
    }
}
