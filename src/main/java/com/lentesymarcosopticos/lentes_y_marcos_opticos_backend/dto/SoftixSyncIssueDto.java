package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

public record SoftixSyncIssueDto(
        String level,
        String sku,
        String field,
        String oldValue,
        String newValue,
        String message) {
}
