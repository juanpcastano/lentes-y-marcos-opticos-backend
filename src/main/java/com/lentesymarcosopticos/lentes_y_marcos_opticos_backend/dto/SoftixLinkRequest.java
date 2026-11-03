package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record SoftixLinkRequest(
        @NotBlank String documentType,
        @NotBlank String documentId) {
}
