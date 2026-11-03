package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

public record SoftixLinkResponse(
        String clientId,
        String documentType,
        String documentId,
        String firstName,
        String lastName,
        String email,
        String mobile) {
}
