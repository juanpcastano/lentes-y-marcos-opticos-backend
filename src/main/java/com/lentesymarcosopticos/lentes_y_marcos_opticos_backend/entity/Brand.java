package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Brand
 */

@Entity
@Table(name = "brands")
@NoArgsConstructor
@Getter
@Setter
public class Brand {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String name;

	@Column
	private String tagline;

	@Column
	private String imageUrl;

	@Column
	private Boolean isFeatured;
}
