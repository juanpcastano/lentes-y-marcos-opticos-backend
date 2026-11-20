package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

public record SoftixImportPreviewDto(
        int updated,
        int hidden,
        int errors,
        int stockChanged,
        int priceChanged,
        int newCount,
        int ignoredCount,
        int blockedCount,
        List<SoftixSyncIssueDto> issues,
        List<SoftixImportNewSkuDto> newSkus,
        List<String> ignoredBrands,
        List<String> ignoredSkus,
        List<String> toCreate,
        List<String> toUpdate,
        List<String> toHide,
        List<String> withError) {
}