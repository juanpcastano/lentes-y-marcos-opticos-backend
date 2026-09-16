package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HeroSlideRequest — creación/edición de slide del hero desde admin.
 * Los destinos (ctaTo/cta2To) deben ser rutas internas (empiezan por /) o
 * vacíos (sin acción). La obligatoriedad por parejas (etiqueta+destino)
 * la valida HeroSlideService.
 */
public record HeroSlideRequest(
		@NotBlank @Size(max = 150) String title,
		@Size(max = 2000) String description,
		@NotBlank @Size(max = 500) String imageUrl,
		@Size(max = 50) String ctaLabel,
		@Size(max = 255) @Pattern(regexp = "^(|/.*)$", message = "El enlace debe ser una ruta interna (ej. /catalog)") String ctaTo,
		@Size(max = 50) String cta2Label,
		@Size(max = 255) @Pattern(regexp = "^(|/.*)$", message = "El enlace debe ser una ruta interna (ej. /catalog)") String cta2To,
		@Min(0) Integer sortOrder,
		Boolean isActive) {
}
