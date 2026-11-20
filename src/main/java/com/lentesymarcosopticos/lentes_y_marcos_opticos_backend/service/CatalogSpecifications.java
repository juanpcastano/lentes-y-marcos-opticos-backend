package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtros del catálogo y del admin sobre Product. Un spec que devuelve null no filtra.
 */
public final class CatalogSpecifications {

	private CatalogSpecifications() {
	}

	/**
	 * Tienda: activo y con stock. Con includeOutOfStock también se muestran los agotados.
	 */
	public static Specification<Product> visible(boolean includeOutOfStock) {
		return (root, query, cb) -> includeOutOfStock
				? cb.equal(root.get("status"), ProductStatus.ACTIVE)
				: cb.and(cb.equal(root.get("status"), ProductStatus.ACTIVE),
						cb.greaterThan(root.get("stock"), 0));
	}

	public static Specification<Product> statusIn(List<ProductStatus> statuses) {
		return (root, query, cb) -> statuses == null || statuses.isEmpty()
				? null
				: root.get("status").in(statuses);
	}

	public static Specification<Product> brandIn(List<String> brands) {
		return (root, query, cb) -> brands == null || brands.isEmpty()
				? null
				: root.get("brand").get("name").in(brands);
	}

	public static Specification<Product> materialIn(List<String> materials) {
		return (root, query, cb) -> materials == null || materials.isEmpty()
				? null
				: root.get("material").in(materials);
	}

	public static Specification<Product> shapeIn(List<String> shapes) {
		return (root, query, cb) -> shapes == null || shapes.isEmpty()
				? null
				: root.get("shape").in(shapes);
	}

	public static Specification<Product> colorIn(List<String> colors) {
		return (root, query, cb) -> colors == null || colors.isEmpty()
				? null
				: root.get("color").in(colors);
	}

	public static Specification<Product> categoryIn(List<String> categories) {
		return (root, query, cb) -> {
			if (categories == null || categories.isEmpty()) {
				return null;
			}
			Subquery<Integer> sub = query.subquery(Integer.class);
			var subRoot = sub.from(Product.class);
			var join = subRoot.join("categories");
			sub.select(cb.literal(1))
					.where(cb.equal(subRoot.get("id"), root.get("id")),
							join.get("name").in(categories));
			return cb.exists(sub);
		};
	}

	public static Specification<Product> priceGte(BigDecimal priceMin) {
		return (root, query, cb) -> priceMin == null
				? null
				: cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), priceMin);
	}

	public static Specification<Product> priceLte(BigDecimal priceMax) {
		return (root, query, cb) -> priceMax == null
				? null
				: cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), priceMax);
	}

	/** Novedad: producto creado hace menos de 30 días (fecha de Softix si está disponible, si no la local). */
	public static Specification<Product> isNew(Boolean isNew) {
		return (root, query, cb) -> {
			if (isNew == null || !isNew) {
				return null;
			}
			LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
			var softixCreated = root.<LocalDateTime>get("softixCreatedAt");
			var localCreated = root.<LocalDateTime>get("createdAt");
			// Si Softix trae fecha, usarla; si no, usar la fecha local.
			var softixBranch = cb.and(cb.isNotNull(softixCreated),
					cb.greaterThanOrEqualTo(softixCreated, cutoff));
			var localBranch = cb.and(cb.isNull(softixCreated),
					cb.greaterThanOrEqualTo(localCreated, cutoff));
			return cb.or(softixBranch, localBranch);
		};
	}

	/** Búsqueda libre: nombre, descripción, marca, material, forma, categorías, color, referencia y SKU. */
	public static Specification<Product> textSearch(String q) {
		return (root, query, cb) -> {
			if (q == null || q.isBlank()) {
				return null;
			}
			String search = "%" + q.toLowerCase().trim() + "%";
			var brandJoin = root.join("brand", JoinType.LEFT);
			Subquery<Integer> categorySearch = query.subquery(Integer.class);
			var categoryRoot = categorySearch.from(Product.class);
			var categoryJoin = categoryRoot.join("categories");
			categorySearch.select(cb.literal(1)).where(
					cb.equal(categoryRoot.get("id"), root.get("id")),
					cb.like(cb.lower(categoryJoin.get("name")), search));
			return cb.or(
					cb.like(cb.lower(root.get("name")), search),
					cb.like(cb.lower(root.get("description")), search),
					cb.like(cb.lower(brandJoin.get("name")), search),
					cb.like(cb.lower(root.get("material")), search),
					cb.like(cb.lower(root.get("shape")), search),
					cb.like(cb.lower(root.get("color")), search),
					cb.like(cb.lower(root.get("reference")), search),
					cb.like(cb.lower(root.get("sku")), search),
					cb.exists(categorySearch));
		};
	}

	/** Orden del catálogo y del admin. */
	public static Sort sortFor(String sort) {
		if (sort == null || sort.isBlank() || "relevance".equals(sort)) {
			return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
		}
		if ("price-asc".equals(sort)) {
			return Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
		}
		if ("price-desc".equals(sort)) {
			return Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
		}
		String[] parts = sort.split(",", 2);
		String field = switch (parts[0].trim()) {
			case "name", "sku", "reference", "color", "stock", "price", "createdAt", "updatedAt", "status" -> parts[0].trim();
			case "brand" -> "brand.name";
			default -> "createdAt";
		};
		boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim());
		return Sort.by(desc ? Sort.Order.desc(field) : Sort.Order.asc(field), Sort.Order.asc("id"));
	}
}
