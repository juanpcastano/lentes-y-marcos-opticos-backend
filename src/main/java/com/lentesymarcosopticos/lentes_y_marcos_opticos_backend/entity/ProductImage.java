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
 * ProductImage
 */

@Entity
@Table(name = "product_images")
@NoArgsConstructor
@Getter
@Setter
public class ProductImage {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(nullable = false)
	private String imageUrl;

	@Column
	private Boolean isPrimary;

	@Column
	private Integer sortOrder;
}
