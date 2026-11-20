package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

public record SoftixSyncPreviewDto(
        int updated,
        int hidden,
        int errors,
        int stockChanged,
        int priceChanged,
        List<SoftixSyncIssueDto> issues) {
}
