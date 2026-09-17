package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * VariantImage — foto de una variante (color). La primera en orden es la
 * primaria que se muestra en el catálogo.
 */
@Entity
@Table(name = "variant_images")
@NoArgsConstructor
@Getter
@Setter
public class VariantImage {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "variant_id", nullable = false)
	private ProductVariant variant;

	@Column(nullable = false)
	private String imageUrl;

	@Column
	private Boolean isPrimary;

	@Column
	private Integer sortOrder;
}
