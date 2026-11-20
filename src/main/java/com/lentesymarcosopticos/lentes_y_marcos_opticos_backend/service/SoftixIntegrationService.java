package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixConnectionStatusDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncIssueDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncLogDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportNewSkuDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixImportPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncPreviewDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixSyncRunDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.IgnoredBrand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.IgnoredSku;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRun;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixSyncRunLog;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.IgnoredBrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.IgnoredSkuRepository;
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
    private final IgnoredBrandRepository ignoredBrandRepository;
    private final IgnoredSkuRepository ignoredSkuRepository;

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

    /** Vista previa de importación: productos nuevos + actualizaciones + errores + marcas ignoradas. */
    @Transactional(readOnly = true)
    public SoftixImportPreviewDto importPreview() {
        var softix = catalogSyncService.fetchCatalog();
        var stock = catalogSyncService.fetchStock();
        var blocking = catalogSyncService.blockingErrors(softix);
        var local = new java.util.HashMap<String, com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product>();
        // reuse local skus via repository? Need productRepository. Not in this service. Use sync service? 
        // We'll fetch local skus through a helper added to catalog sync service.
        var localSkus = catalogSyncService.localSkus();
        Set<String> ignoredBrands = ignoredBrandNames();
        Set<String> ignoredSkus = ignoredSkuNames();
        SyncResult syncResult = catalogSyncService.preview();

        List<SoftixImportNewSkuDto> newSkus = new ArrayList<>();
        int ignoredCount = 0;
        int blockedCount = 0;
        List<String> toCreate = new ArrayList<>();
        for (var item : softix.values()) {
            String sku = item.code().trim();
            if (localSkus.contains(sku)) {
                continue;
            }
            String brandName = SoftixCatalogSyncService.normalizePublic(item.brand());
            String categoryName = SoftixCatalogSyncService.normalizePublic(item.category());
            boolean blocked = blocking.containsKey(sku);
            boolean ignoredFlag = (brandName != null && ignoredBrands.contains(brandName))
                    || ignoredSkus.contains(sku);
            if (blocked) {
                blockedCount++;
            }
            if (ignoredFlag) {
                ignoredCount++;
            }
            newSkus.add(new SoftixImportNewSkuDto(
                    sku,
                    brandName,
                    trimOrNull(item.reference()),
                    trimOrNull(item.color()),
                    categoryName,
                    trimOrNull(item.name()),
                    item.salePrice() == null ? java.math.BigDecimal.ZERO : item.salePrice(),
                    Math.max(0, stock.getOrDefault(sku, 0)),
                    blocked,
                    blocked ? blocking.get(sku) : null,
                    ignoredFlag));
            if (!blocked && !ignoredFlag) {
                toCreate.add(sku);
            }
        }
        newSkus.sort(java.util.Comparator.comparing(SoftixImportNewSkuDto::sku));

        java.util.Set<String> updatedSet = new java.util.LinkedHashSet<>();
        java.util.Set<String> hiddenSet = new java.util.LinkedHashSet<>();
        java.util.Set<String> errorSet = new java.util.LinkedHashSet<>();
        for (var entry : syncResult.entries()) {
            if (entry.sku() == null) continue;
            if (entry.level().equals("ERROR")) {
                errorSet.add(entry.sku());
            } else if ("estado".equals(entry.field()) && "HIDDEN".equals(entry.newValue())) {
                hiddenSet.add(entry.sku());
            } else if (entry.field() != null) {
                updatedSet.add(entry.sku());
            }
        }
        // blocked new skus are also errors
        for (var ns : newSkus) {
            if (ns.blocked()) errorSet.add(ns.sku());
        }

        return new SoftixImportPreviewDto(
                syncResult.updated(), syncResult.hidden(), syncResult.errors(),
                syncResult.stockChanged(), syncResult.priceChanged(),
                (int) newSkus.stream().filter(s -> !s.blocked() && !s.ignored()).count(),
                ignoredCount, blockedCount,
                syncResult.entries().stream().map(this::toIssueDto).toList(),
                newSkus,
                new ArrayList<>(ignoredBrands),
                new ArrayList<>(ignoredSkus),
                toCreate,
                new ArrayList<>(updatedSet),
                new ArrayList<>(hiddenSet),
                new ArrayList<>(errorSet));
    }

    /** Ejecuta la importación: crea productos nuevos y luego sincroniza con origen IMPORT. */
    public synchronized SoftixSyncRunDto runImport() {
        if (runRepository.existsByStatus(SyncRunStatus.RUNNING)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya hay una sincronización en curso");
        }
        Set<String> ignoredBrands = ignoredBrandNames();
        Set<String> ignoredSkus = ignoredSkuNames();
        List<String> createdSkus = catalogSyncService.importNewProducts(ignoredBrands, ignoredSkus);
        // blocked new SKUs (not created)
        java.util.Set<String> local = catalogSyncService.localSkus();
        java.util.Map<String, com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.ProductItem> softix =
                catalogSyncService.fetchCatalog();
        java.util.Map<String, String> blocking = catalogSyncService.blockingErrors(softix);
        List<String> blockedSkus = new ArrayList<>();
        for (String sku : softix.keySet()) {
            String s = sku.trim();
            if (local.contains(s) || !blocking.containsKey(s)) continue;
            blockedSkus.add(s);
        }

        SoftixSyncRunDto run = runSync(SyncRunTrigger.IMPORT);
        SoftixSyncRun r = runRepository.findById(run.id()).orElse(null);
        if (r != null) {
            List<SoftixSyncRunLog> logs = new ArrayList<>();
            for (String sku : createdSkus) {
                logs.add(new SoftixSyncRunLog(r.getId(), SoftixCatalogSyncService.LEVEL_INFO, sku,
                        "creación", null, sku, "Producto nuevo importado"));
            }
            for (String sku : blockedSkus) {
                logs.add(new SoftixSyncRunLog(r.getId(), SoftixCatalogSyncService.LEVEL_ERROR, sku,
                        null, null, null, blocking.get(sku)));
            }
            if (!logs.isEmpty()) {
                logRepository.saveAll(logs);
            }
        }
        return run;
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
        if (!ignoredBrandRepository.existsByName(normalized)) {
            IgnoredBrand b = new IgnoredBrand();
            b.setName(normalized);
            ignoredBrandRepository.save(b);
        }
    }

    @Transactional
    public void unignoreBrand(String name) {
        ignoredBrandRepository.deleteByName(normalizeName(name));
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
