package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.util.List;

public record SoftixSyncRunDetailDto(
        SoftixSyncRunDto run,
        List<SoftixSyncEventDto> logs) {
}
