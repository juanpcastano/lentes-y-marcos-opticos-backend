package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;

/**
 * ProductSpecifications — filtros dinámicos a nivel producto (panel admin).
 *
 * El precio y el descuento viven en la variante: los filtros de precio y
 * oferta se resuelven con EXISTS sobre las variantes activas del producto.
 */
public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	/**
	 * Estado derivado del producto: activo si tiene al menos una variante
	 * activa (la disponibilidad vive en la variante, no en el producto).
	 */
	public static Specification<Product> hasActiveVariant(Boolean active) {
		return (root, query, cb) -> {
			if (active == null) {
				return null;
			}
			Subquery<Integer> sub = query.subquery(Integer.class);
			var variant = sub.from(ProductVariant.class);
			sub.select(cb.literal(1)).where(
					cb.equal(variant.get("product").get("id"), root.get("id")),
					cb.isTrue(variant.get("isActive")));
			return active ? cb.exists(sub) : cb.not(cb.exists(sub));
		};
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

	/**
	 * Color en admin: el producto tiene alguna variante (activa o no) de ese
	 * color. En tienda el filtro equivalente es por variante visible.
	 */
	public static Specification<Product> colorIn(List<String> colors) {
		return (root, query, cb) -> {
			if (colors == null || colors.isEmpty()) {
				return null;
			}
			Subquery<Integer> sub = query.subquery(Integer.class);
			var variant = sub.from(ProductVariant.class);
			sub.select(cb.literal(1)).where(
					cb.equal(variant.get("product").get("id"), root.get("id")),
					variant.get("color").in(colors));
			return cb.exists(sub);
		};
	}

	/**
	 * EXISTS una variante activa del producto cuyo precio final cumple el
	 * predicado dado (rango de precio / oferta).
	 */
	private static Specification<Product> variantExists(
			java.util.function.BiFunction<jakarta.persistence.criteria.Root<ProductVariant>, jakarta.persistence.criteria.CriteriaBuilder, jakarta.persistence.criteria.Predicate> condition) {
		return (root, query, cb) -> {
			Subquery<Integer> sub = query.subquery(Integer.class);
			var variant = sub.from(ProductVariant.class);
			sub.select(cb.literal(1)).where(
					cb.equal(variant.get("product").get("id"), root.get("id")),
					cb.isTrue(variant.get("isActive")),
					condition.apply(variant, cb));
			return cb.exists(sub);
		};
	}

	public static Specification<Product> priceGte(Integer priceMin) {
		if (priceMin == null) {
			return (root, query, cb) -> null;
		}
		return variantExists((variant, cb) -> cb.greaterThanOrEqualTo(
				VariantSpecifications.finalPrice(variant, cb), priceMin.doubleValue()));
	}

	public static Specification<Product> priceLte(Integer priceMax) {
		if (priceMax == null) {
			return (root, query, cb) -> null;
		}
		return variantExists((variant, cb) -> cb.lessThanOrEqualTo(
				VariantSpecifications.finalPrice(variant, cb), priceMax.doubleValue()));
	}

	/** En oferta: el producto tiene alguna variante activa con descuento > 0. */
	public static Specification<Product> onSale(Boolean onSale) {
		if (onSale == null || !onSale) {
			return (root, query, cb) -> null;
		}
		return variantExists((variant, cb) -> cb.greaterThan(variant.get("discountPercentage"), 0));
	}

	/** Novedad: mismo criterio que el badge NUEVO (creado hace < 30 días) */
	public static Specification<Product> isNew(Boolean isNew) {
		return (root, query, cb) -> isNew == null || !isNew
				? null
				: cb.greaterThanOrEqualTo(root.get("createdAt"),
						java.time.LocalDateTime.now().minusDays(30));
	}

	/** Búsqueda libre: nombre, descripción, marca, material, forma, categorías, color y SKU. */
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
			Subquery<Integer> variantSearch = query.subquery(Integer.class);
			var variantRoot = variantSearch.from(ProductVariant.class);
			variantSearch.select(cb.literal(1)).where(
					cb.equal(variantRoot.get("product").get("id"), root.get("id")),
					cb.or(
							cb.like(cb.lower(variantRoot.get("color")), search),
							cb.like(cb.lower(variantRoot.get("sku")), search)));
			return cb.or(
					cb.like(cb.lower(root.get("name")), search),
					cb.like(cb.lower(root.get("description")), search),
					cb.like(cb.lower(brandJoin.get("name")), search),
					cb.like(cb.lower(root.get("material")), search),
					cb.like(cb.lower(root.get("shape")), search),
					cb.exists(categorySearch),
					cb.exists(variantSearch));
		};
	}
}
