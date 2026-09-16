package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HeroSlide — slide del hero de la página principal, gestionado desde el admin.
 */
@Entity
@Table(name = "hero_slides")
@NoArgsConstructor
@Getter
@Setter
public class HeroSlide {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Column(name = "cta_label", length = 50)
	private String ctaLabel;

	@Column(name = "cta_to", length = 255)
	private String ctaTo;

	@Column(name = "cta2_label", length = 50)
	private String cta2Label;

	@Column(name = "cta2_to", length = 255)
	private String cta2To;

	@Column(name = "sort_order", nullable = false)
	private Integer sortOrder = 0;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
		if (sortOrder == null) {
			sortOrder = 0;
		}
		if (isActive == null) {
			isActive = true;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}
}
