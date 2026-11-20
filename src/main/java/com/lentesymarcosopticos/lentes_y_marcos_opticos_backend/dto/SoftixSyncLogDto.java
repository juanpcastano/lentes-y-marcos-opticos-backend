package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDateTime;

public record SoftixSyncLogDto(
        Long id,
        String level,
        String sku,
        String field,
        String oldValue,
        String newValue,
        String message,
        LocalDateTime createdAt) {
}
