package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDateTime;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventAction;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventEntity;

public record SoftixSyncEventDto(
        Long id,
        SyncEventEntity entityType,
        String entityKey,
        SyncEventAction action,
        String level,
        String field,
        String oldValue,
        String newValue,
        String message,
        LocalDateTime createdAt) {
}
