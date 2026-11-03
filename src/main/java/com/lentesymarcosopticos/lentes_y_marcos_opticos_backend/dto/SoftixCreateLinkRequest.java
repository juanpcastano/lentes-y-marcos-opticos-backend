package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SoftixCreateLinkRequest(
        @NotBlank String documentType,
        @NotBlank String documentId,
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        String secondLastName,
        @NotBlank String mobile,
        @NotBlank @Email String email,
        @NotNull LocalDate birthDate,
        String city,
        String locality,
        String address) {
}
