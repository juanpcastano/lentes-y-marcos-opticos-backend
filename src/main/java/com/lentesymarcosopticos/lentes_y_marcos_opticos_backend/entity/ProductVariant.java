package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ProductVariant — unidad vendible y visible del catálogo (producto + color).
 * Cada variante tiene su propio color, SKU (código Softix), precio,
 * descuento e imágenes.
 */
@Entity
@Table(name = "product_variants")
@NoArgsConstructor
@Getter
@Setter
public class ProductVariant {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private String color;

	@Column(nullable = false, unique = true)
	private String sku;

	@Column(nullable = false)
	private Integer price;

	@Column
	private Integer discountPercentage;

	@Column
	private Boolean isActive;

	@OneToMany(mappedBy = "variant", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder")
	private Set<VariantImage> images = new LinkedHashSet<>();
}
