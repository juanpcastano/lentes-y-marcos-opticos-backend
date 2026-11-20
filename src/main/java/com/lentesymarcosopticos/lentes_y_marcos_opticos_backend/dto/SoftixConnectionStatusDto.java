package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDateTime;

public record SoftixConnectionStatusDto(
        boolean connected,
        String message,
        LocalDateTime checkedAt) {
}
