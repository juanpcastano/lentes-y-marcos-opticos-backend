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
 * ProductVariant
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

	@Column
	private String variantName;

	@Column
	private String variantValue;

	@Column(unique = true)
	private String sku;

	@Column
	private Integer priceAdjustment;

	@Column
	private String imageUrl;

	@Column
	private Boolean isActive;
}
