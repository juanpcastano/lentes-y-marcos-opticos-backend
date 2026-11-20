package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SoftixCreateLinkRequest(
        @NotBlank @Size(max = 2) String documentType,
        @NotBlank @Pattern(regexp = "\\d{1,10}") String documentId,
        @NotBlank String firstName,
        String middleName,
        @NotBlank String lastName,
        String secondLastName,
        @NotBlank @Size(min = 10, max = 10) String mobile,
        @NotBlank @Email String email,
        @NotNull LocalDate birthDate,
        @NotBlank String gender,
        @NotBlank String city,
        @NotBlank String locality,
        String address) {
}
