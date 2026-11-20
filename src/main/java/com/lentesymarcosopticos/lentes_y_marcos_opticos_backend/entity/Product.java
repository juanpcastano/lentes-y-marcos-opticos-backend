package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Product — un SKU de Softix. Los datos de Softix (SKU, marca, referencia, nombre,
 * color, categoría, precio y stock) son de solo lectura en el admin.
 */
@Entity
@Table(name = "products")
@NoArgsConstructor
@Getter
@Setter
public class Product {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String sku;

	@Column(nullable = false)
	private String name;

	@Column
	private String reference;

	@Column
	private String color;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brand_id")
	private Brand brand;

	@Column(precision = 14, scale = 2)
	private BigDecimal price;

	@Column(nullable = false)
	private Boolean saleVat = false;

	@Column(nullable = false)
	private Integer stock = 0;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ProductStatus status = ProductStatus.INACTIVE;

	@Column(nullable = false)
	private Boolean softixMissing = false;

	@Column
	private LocalDateTime syncedAt;

	/** Fecha de creacion del producto segun Softix (campo creationDate). */
	@Column(name = "softix_created_at")
	private LocalDateTime softixCreatedAt;

	@Column
	private String material;

	@Column
	private String shape;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column
	private String productType;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private LocalDateTime updatedAt;

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "product_categories", joinColumns = @JoinColumn(name = "product_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
	private Set<Category> categories = new LinkedHashSet<>();

	@OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortOrder")
	private Set<ProductImage> images = new LinkedHashSet<>();
}
