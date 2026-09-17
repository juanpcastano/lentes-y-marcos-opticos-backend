package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;

/**
 * VariantSpecifications — filtros dinámicos del catálogo.
 *
 * El catálogo muestra una fila por variante activa de producto activo
 * (producto + color): un producto multicolor aparece varias veces.
 */
public final class VariantSpecifications {

	private VariantSpecifications() {
	}

	/** Visible en tienda: la variante está activa. */
	public static Specification<ProductVariant> isVisible() {
		return (root, query, cb) -> cb.isTrue(root.get("isActive"));
	}

	public static Specification<ProductVariant> brandIn(List<String> brands) {
		return (root, query, cb) -> brands == null || brands.isEmpty()
				? null
				: root.get("product").get("brand").get("name").in(brands);
	}

	public static Specification<ProductVariant> materialIn(List<String> materials) {
		return (root, query, cb) -> materials == null || materials.isEmpty()
				? null
				: root.get("product").get("material").in(materials);
	}

	public static Specification<ProductVariant> shapeIn(List<String> shapes) {
		return (root, query, cb) -> shapes == null || shapes.isEmpty()
				? null
				: root.get("product").get("shape").in(shapes);
	}

	public static Specification<ProductVariant> colorIn(List<String> colors) {
		return (root, query, cb) -> colors == null || colors.isEmpty()
				? null
				: root.get("color").in(colors);
	}

	public static Specification<ProductVariant> categoryIn(List<String> categories) {
		return (root, query, cb) -> {
			if (categories == null || categories.isEmpty()) {
				return null;
			}
			Subquery<Integer> sub = query.subquery(Integer.class);
			var subRoot = sub.from(Product.class);
			var join = subRoot.join("categories");
			sub.select(cb.literal(1))
					.where(cb.equal(subRoot.get("id"), root.get("product").get("id")),
							join.get("name").in(categories));
			return cb.exists(sub);
		};
	}

	/** Precio final con descuento: price * (1 - COALESCE(discount,0)/100) */
	public static jakarta.persistence.criteria.Expression<Double> finalPrice(
			jakarta.persistence.criteria.Root<ProductVariant> root,
			jakarta.persistence.criteria.CriteriaBuilder cb) {
		jakarta.persistence.criteria.Expression<Double> base = root.get("price").as(Double.class);
		jakarta.persistence.criteria.Expression<Integer> discountRaw = root
				.<Integer>get("discountPercentage");
		jakarta.persistence.criteria.Expression<Double> discount = cb.coalesce(discountRaw, 0)
				.as(Double.class);
		jakarta.persistence.criteria.Expression<Double> fraction = cb.prod(discount, 0.01);
		jakarta.persistence.criteria.Expression<Double> factor = cb.diff(1.0, fraction);
		return cb.prod(base, factor);
	}

	public static Specification<ProductVariant> priceGte(Integer priceMin) {
		return (root, query, cb) -> priceMin == null
				? null
				: cb.greaterThanOrEqualTo(finalPrice(root, cb), priceMin.doubleValue());
	}

	public static Specification<ProductVariant> priceLte(Integer priceMax) {
		return (root, query, cb) -> priceMax == null
				? null
				: cb.lessThanOrEqualTo(finalPrice(root, cb), priceMax.doubleValue());
	}

	/** En oferta: mismo criterio que el badge OFERTA (descuento > 0). */
	public static Specification<ProductVariant> onSale(Boolean onSale) {
		return (root, query, cb) -> onSale == null || !onSale
				? null
				: cb.greaterThan(root.get("discountPercentage"), 0);
	}

	/** Novedad: mismo criterio que el badge NUEVO (producto creado hace < 30 días). */
	public static Specification<ProductVariant> isNew(Boolean isNew) {
		return (root, query, cb) -> isNew == null || !isNew
				? null
				: cb.greaterThanOrEqualTo(root.get("product").get("createdAt"),
						java.time.LocalDateTime.now().minusDays(30));
	}

	/** Búsqueda libre: nombre, descripción, marca, material, forma, categorías, color y SKU. */
	public static Specification<ProductVariant> textSearch(String q) {
		return (root, query, cb) -> {
			if (q == null || q.isBlank()) {
				return null;
			}
			String search = "%" + q.toLowerCase().trim() + "%";
			var product = root.get("product");
			var brandJoin = root.join("product", JoinType.INNER).join("brand", JoinType.LEFT);
			Subquery<Integer> categorySearch = query.subquery(Integer.class);
			var categoryRoot = categorySearch.from(Product.class);
			var categoryJoin = categoryRoot.join("categories");
			categorySearch.select(cb.literal(1)).where(
					cb.equal(categoryRoot.get("id"), product.get("id")),
					cb.like(cb.lower(categoryJoin.get("name")), search));
			return cb.or(
					cb.like(cb.lower(product.get("name")), search),
					cb.like(cb.lower(product.get("description")), search),
					cb.like(cb.lower(brandJoin.get("name")), search),
					cb.like(cb.lower(product.get("material")), search),
					cb.like(cb.lower(product.get("shape")), search),
					cb.like(cb.lower(root.get("color")), search),
					cb.like(cb.lower(root.get("sku")), search),
					cb.exists(categorySearch));
		};
	}

	public static Specification<ProductVariant> combine(List<String> categories, List<String> brands,
			List<String> materials, List<String> shapes, List<String> colors, Integer priceMin,
			Integer priceMax, String sort, Boolean onSale, Boolean isNew, String q) {
		Specification<ProductVariant> filters = isVisible().and(categoryIn(categories))
				.and(brandIn(brands)).and(materialIn(materials)).and(shapeIn(shapes))
				.and(colorIn(colors)).and(priceGte(priceMin)).and(priceLte(priceMax))
				.and(onSale(onSale)).and(isNew(isNew)).and(textSearch(q));

		return (root, query, cb) -> {
			if (query.getResultType() != Long.class && query.getResultType() != long.class) {
				var price = finalPrice(root, cb);
				var order = switch (sort == null ? "relevance" : sort) {
					case "price-asc" -> cb.asc(price);
					case "price-desc" -> cb.desc(price);
					default -> cb.desc(root.get("product").get("createdAt"));
				};
				query.orderBy(order);
			}
			Predicate predicate = filters.toPredicate(root, query, cb);
			// Sin DISTINCT a propósito: Postgres lo prohíbe combinado con
			// ORDER BY sobre columnas no seleccionadas (p. ej. el createdAt
			// del producto en sort=relevance), y no hace falta porque ningún
			// join del spec es a-muchos (solo navegaciones a-uno y EXISTS).
			return predicate;
		};
	}
}
