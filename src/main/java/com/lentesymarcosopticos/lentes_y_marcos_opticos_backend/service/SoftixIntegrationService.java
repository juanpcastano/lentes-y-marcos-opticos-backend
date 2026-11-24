package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixConnectionStatusDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportNewSkuDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncEventDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncIssueDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.IgnoredBrand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.IgnoredSku;
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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.SyncResult;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * SoftixIntegrationService — orquesta la integración con Softix desde el panel:
 * verificación de conexión, vista previa, ejecución y registro de cada sincronización.
 *
 * Cada ejecución se crea antes de tocar productos. La importación y la sincronización corren en
 * una sola transacción junto con sus eventos; si falla, se revierte todo y la ejecución queda
 * como FAILED en una transacción aparte.
 */
@Service
@Slf4j
@AllArgsConstructor
public class SoftixIntegrationService {
    private static final String PRODUCT_TYPE = "MONTURA";
    private static final String UNEXPECTED_ERROR = "Error inesperado durante la sincronización";
    private static final String ORPHAN_MESSAGE = "Interrumpida: la aplicación se reinició durante la ejecución";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOG_RETENTION_DAYS = 90;

    private final SoftixClient softixClient;
    private final SoftixCatalogSyncService catalogSyncService;
    private final SoftixSyncRunRepository runRepository;
    private final SoftixSyncRunEventRepository eventRepository;
    private final IgnoredBrandRepository ignoredBrandRepository;
    private final IgnoredSkuRepository ignoredSkuRepository;
    private final TransactionTemplate transactionTemplate;

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
        Set<String> newBrands = new TreeSet<>();
        Set<String> newCategories = new TreeSet<>();
        for (Entry e : result.entries()) {
            if (e.action() == SyncEventAction.CREATED && e.entityType() == SyncEventEntity.BRAND) {
                newBrands.add(e.key());
            }
            if (e.action() == SyncEventAction.CREATED && e.entityType() == SyncEventEntity.CATEGORY) {
                newCategories.add(e.key());
            }
        }
        return new SoftixSyncPreviewDto(
                result.updated(), result.hidden(), result.errors(),
                result.stockChanged(), result.priceChanged(),
                new ArrayList<>(newBrands),
                new ArrayList<>(newCategories),
                result.entries().stream().map(this::toIssueDto).toList());
    }

    /** Vista previa de importación: productos nuevos + actualizaciones + errores + marcas ignoradas. */
    @Transactional(readOnly = true)
    public SoftixImportPreviewDto importPreview() {
        Set<String> ignoredBrands = ignoredBrandNames();
        Set<String> ignoredSkus = ignoredSkuNames();
        SyncResult syncResult = catalogSyncService.preview();
        var importPreview = catalogSyncService.previewImport(ignoredBrands, ignoredSkus);

        List<SoftixImportNewSkuDto> newSkus = importPreview.newSkus().stream()
                .map(this::toNewSkuDto)
                .toList();
        List<String> toCreate = importPreview.newSkus().stream()
                .filter(s -> s.state() == SoftixCatalogSyncService.NewSkuState.VALID)
                .map(SoftixCatalogSyncService.NewSku::sku)
                .toList();
        List<String> blockedSkus = importPreview.newSkus().stream()
                .filter(s -> s.state() == SoftixCatalogSyncService.NewSkuState.BLOCKED)
                .map(SoftixCatalogSyncService.NewSku::sku)
                .toList();
        int ignoredCount = (int) importPreview.newSkus().stream()
                .filter(s -> s.state() == SoftixCatalogSyncService.NewSkuState.IGNORED_BY_BRAND
                        || s.state() == SoftixCatalogSyncService.NewSkuState.IGNORED_BY_SKU)
                .count();

        Set<String> newBrandSet = new TreeSet<>(importPreview.brandsToCreate());
        Set<String> newCategorySet = new TreeSet<>(importPreview.categoriesToCreate());
        for (Entry e : syncResult.entries()) {
            if (e.action() == SyncEventAction.CREATED && e.entityType() == SyncEventEntity.BRAND) {
                newBrandSet.add(e.key());
            }
            if (e.action() == SyncEventAction.CREATED && e.entityType() == SyncEventEntity.CATEGORY) {
                newCategorySet.add(e.key());
            }
        }

        Set<String> updatedSet = new java.util.LinkedHashSet<>();
        Set<String> hiddenSet = new java.util.LinkedHashSet<>();
        Set<String> errorSet = new java.util.LinkedHashSet<>();
        for (Entry entry : syncResult.entries()) {
            if (entry.entityType() != SyncEventEntity.PRODUCT) {
                continue;
            }
            if (entry.action() == SyncEventAction.BLOCKED) {
                errorSet.add(entry.key());
            } else if (entry.action() == SyncEventAction.HIDDEN) {
                hiddenSet.add(entry.key());
            } else {
                updatedSet.add(entry.key());
            }
        }
        errorSet.addAll(blockedSkus);

        return new SoftixImportPreviewDto(
                syncResult.updated(), syncResult.hidden(), syncResult.errors() + blockedSkus.size(),
                syncResult.stockChanged(), syncResult.priceChanged(),
                toCreate.size(), ignoredCount, blockedSkus.size(),
                syncResult.entries().stream().map(this::toIssueDto).toList(),
                newSkus,
                new ArrayList<>(ignoredBrands),
                new ArrayList<>(ignoredSkus),
                new ArrayList<>(newBrandSet),
                new ArrayList<>(newCategorySet),
                toCreate,
                new ArrayList<>(updatedSet),
                new ArrayList<>(hiddenSet),
                new ArrayList<>(errorSet));
    }

    /** Importa los SKUs válidos y luego sincroniza, en una sola ejecución con origen IMPORT. */
    public synchronized SoftixSyncRunDto runImport() {
        return execute(SyncRunTrigger.IMPORT, true);
    }

    /**
     * Ejecuta y registra una sincronización. Si falla, nada se aplica y la ejecución queda como FAILED.
     */
    public synchronized SoftixSyncRunDto runSync(SyncRunTrigger trigger) {
        return execute(trigger, false);
    }

    private SoftixSyncRunDto execute(SyncRunTrigger trigger, boolean withImport) {
        if (runRepository.existsByStatus(SyncRunStatus.RUNNING)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay una sincronización en curso");
        }
        eventRepository.deleteCreatedBefore(LocalDateTime.now().minusDays(LOG_RETENTION_DAYS));
        UUID runId = runRepository.save(new SoftixSyncRun(trigger, LocalDateTime.now())).getId();
        try {
            return transactionTemplate.execute(status -> applyRun(runId, withImport));
        } catch (RuntimeException ex) {
            String message = safeMessage(ex);
            log.warn("Sincronización Softix fallida: {}", message);
            markFailed(runId, message);
            throw new ApiException(HttpStatus.BAD_GATEWAY, message);
        }
    }

    private SoftixSyncRunDto applyRun(UUID runId, boolean withImport) {
        Set<String> ignoredBrands = ignoredBrandNames();
        Set<String> ignoredSkus = ignoredSkuNames();
        List<Entry> entries = new ArrayList<>();
        int created = 0;
        int importErrors = 0;

        if (withImport) {
            ImportResult imported = catalogSyncService.importNewProducts(ignoredBrands, ignoredSkus);
            created = imported.createdSkus().size();
            for (String sku : imported.createdSkus()) {
                entries.add(new Entry(SoftixCatalogSyncService.LEVEL_INFO, SyncEventEntity.PRODUCT,
                        SyncEventAction.CREATED, sku, null, null, null, "Producto nuevo importado"));
            }
            for (String brand : imported.createdBrands()) {
                entries.add(new Entry(SoftixCatalogSyncService.LEVEL_INFO, SyncEventEntity.BRAND,
                        SyncEventAction.CREATED, brand, null, null, null, "Marca nueva"));
            }
            for (String category : imported.createdCategories()) {
                entries.add(new Entry(SoftixCatalogSyncService.LEVEL_INFO, SyncEventEntity.CATEGORY,
                        SyncEventAction.CREATED, category, null, null, null, "Categoría nueva"));
            }
            for (var blocked : imported.blocked()) {
                entries.add(new Entry(SoftixCatalogSyncService.LEVEL_ERROR, SyncEventEntity.PRODUCT,
                        SyncEventAction.BLOCKED, blocked.sku(), null, null, null, blocked.blockReason()));
                importErrors++;
            }
        }

        SyncResult result = catalogSyncService.sync();
        entries.addAll(result.entries());

        int errors = result.errors() + importErrors;
        int restored = countOf(entries, SyncEventEntity.PRODUCT, SyncEventAction.RESTORED);
        int newBrands = countOf(entries, SyncEventEntity.BRAND, SyncEventAction.CREATED);
        int newCategories = countOf(entries, SyncEventEntity.CATEGORY, SyncEventAction.CREATED);
        entries.add(new Entry(SoftixCatalogSyncService.LEVEL_INFO, SyncEventEntity.RUN, SyncEventAction.SUMMARY,
                null, null, null, null, String.format(
                        "Terminada: %d creados, %d actualizados, %d ocultos, %d restaurados, %d con error, "
                                + "%d marcas nuevas, %d categorías nuevas, %d cambios de stock, %d cambios de precio",
                        created, result.updated(), result.hidden(), restored, errors,
                        newBrands, newCategories, result.stockChanged(), result.priceChanged())));

        SoftixSyncRun run = runRepository.findById(runId).orElseThrow();
        run.setCreatedCount(created);
        run.setUpdatedCount(result.updated());
        run.setHiddenCount(result.hidden());
        run.setRestoredCount(restored);
        run.setErrorCount(errors);
        run.setNewBrandCount(newBrands);
        run.setNewCategoryCount(newCategories);
        run.setStatus(errors > 0 ? SyncRunStatus.PARTIAL : SyncRunStatus.SUCCESS);
        run.setFinishedAt(LocalDateTime.now());
        eventRepository.saveAll(entries.stream().map(e -> toEvent(runId, e)).toList());
        return toDto(run);
    }

    private void markFailed(UUID runId, String message) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(SyncRunStatus.FAILED);
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(message);
            runRepository.save(run);
        });
        eventRepository.save(new SoftixSyncRunEvent(runId, SyncEventEntity.RUN, null, SyncEventAction.SUMMARY,
                SoftixCatalogSyncService.LEVEL_ERROR, null, null, null, message));
    }

    /** Al arrancar, las ejecuciones que quedaron en RUNNING no pueden seguir: se marcan como FAILED. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failOrphanedRuns() {
        LocalDateTime now = LocalDateTime.now();
        for (SoftixSyncRun run : runRepository.findByStatus(SyncRunStatus.RUNNING)) {
            run.setStatus(SyncRunStatus.FAILED);
            run.setFinishedAt(now);
            run.setErrorMessage(ORPHAN_MESSAGE);
            eventRepository.save(new SoftixSyncRunEvent(run.getId(), SyncEventEntity.RUN, null,
                    SyncEventAction.SUMMARY, SoftixCatalogSyncService.LEVEL_ERROR, null, null, null, ORPHAN_MESSAGE));
        }
    }

    @Transactional(readOnly = true)
    public List<String> listIgnoredBrands() {
        return ignoredBrandRepository.findAllByOrderByNameAsc().stream()
                .map(IgnoredBrand::getName).toList();
    }

    @Transactional
    public void ignoreBrand(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nombre de marca inválido");
        }
        boolean exists = ignoredBrandRepository.findAll().stream()
                .anyMatch(ib -> ib.getName().equals(normalized));
        if (!exists) {
            IgnoredBrand b = new IgnoredBrand();
            b.setName(normalized);
            ignoredBrandRepository.save(b);
        }
    }

    @Transactional
    public void unignoreBrand(String name) {
        String normalized = normalizeName(name);
        ignoredBrandRepository.findAll().stream()
                .filter(ib -> ib.getName().equals(normalized))
                .forEach(ib -> ignoredBrandRepository.delete(ib));
    }

    @Transactional(readOnly = true)
    public List<String> listIgnoredSkus() {
        return ignoredSkuRepository.findAllByOrderBySkuAsc().stream()
                .map(IgnoredSku::getSku).toList();
    }

    @Transactional
    public void ignoreSku(String sku) {
        String s = sku == null ? null : sku.trim();
        if (s == null || s.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SKU inválido");
        }
        if (!ignoredSkuRepository.existsBySku(s)) {
            IgnoredSku is = new IgnoredSku();
            is.setSku(s);
            ignoredSkuRepository.save(is);
        }
    }

    @Transactional
    public void unignoreSku(String sku) {
        ignoredSkuRepository.deleteBySku(sku == null ? null : sku.trim());
    }

    private Set<String> ignoredSkuNames() {
        return new HashSet<>(ignoredSkuRepository.findAll().stream()
                .map(IgnoredSku::getSku).toList());
    }

    private Set<String> ignoredBrandNames() {
        return new HashSet<>(ignoredBrandRepository.findAll().stream()
                .map(IgnoredBrand::getName).toList());
    }

    private static int countOf(List<Entry> entries, SyncEventEntity entityType, SyncEventAction action) {
        return (int) entries.stream()
                .filter(e -> e.entityType() == entityType && e.action() == action)
                .count();
    }

    private static String normalizeName(String value) {
        if (value == null) return null;
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private SoftixImportNewSkuDto toNewSkuDto(SoftixCatalogSyncService.NewSku sku) {
        return new SoftixImportNewSkuDto(
                sku.sku(),
                sku.brand(),
                sku.reference(),
                sku.color(),
                sku.category(),
                sku.name(),
                sku.price(),
                sku.stock(),
                sku.state().name(),
                sku.blockReason());
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
        List<SoftixSyncEventDto> events = eventRepository.findByRunIdOrderByIdAsc(id).stream()
                .map(this::toEventDto)
                .toList();
        return new SoftixSyncRunDetailDto(toDto(run), events);
    }

    private static String safeMessage(RuntimeException ex) {
        if (ex instanceof SoftixClientException || ex instanceof ApiException
                || (ex instanceof IllegalStateException && ex.getMessage() != null
                        && ex.getMessage().startsWith("Softix devolvió"))) {
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
                run.getCreatedCount(),
                run.getUpdatedCount(),
                run.getHiddenCount(),
                run.getRestoredCount(),
                run.getErrorCount(),
                run.getNewBrandCount(),
                run.getNewCategoryCount(),
                run.getErrorMessage());
    }

    private static SoftixSyncRunEvent toEvent(UUID runId, Entry entry) {
        return new SoftixSyncRunEvent(runId, entry.entityType(), entry.key(), entry.action(), entry.level(),
                entry.field(), entry.oldValue(), entry.newValue(), entry.message());
    }

    private SoftixSyncEventDto toEventDto(SoftixSyncRunEvent event) {
        return new SoftixSyncEventDto(event.getId(), event.getEntityType(), event.getEntityKey(),
                event.getAction(), event.getLevel(), event.getField(), event.getOldValue(),
                event.getNewValue(), event.getMessage(), event.getCreatedAt());
    }

    private SoftixSyncIssueDto toIssueDto(Entry entry) {
        String sku = entry.entityType() == SyncEventEntity.PRODUCT ? entry.key() : null;
        return new SoftixSyncIssueDto(entry.level(), sku, entry.field(), entry.oldValue(),
                entry.newValue(), entry.message());
    }
}
