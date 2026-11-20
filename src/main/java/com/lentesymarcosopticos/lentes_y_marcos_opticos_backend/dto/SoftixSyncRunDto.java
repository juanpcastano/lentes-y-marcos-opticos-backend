package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncRunTrigger;

public record SoftixSyncRunDto(
        UUID id,
        SyncRunTrigger trigger,
        SyncRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int updated,
        int hidden,
        int errors,
        String errorMessage) {
}
