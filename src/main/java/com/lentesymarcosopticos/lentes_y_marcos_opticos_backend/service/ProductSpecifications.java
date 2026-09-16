package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;

/**
 * ProductSpecifications — filtros dinámicos del catálogo
 */
public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<Product> isActive() {
		return (root, query, cb) -> cb.isTrue(root.get("isActive"));
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

	/** Precio final con descuento: base_price * (1 - COALESCE(discount,0)/100) */
	public static jakarta.persistence.criteria.Expression<Double> discountedPrice(
			jakarta.persistence.criteria.Root<Product> root, jakarta.persistence.criteria.CriteriaBuilder cb) {
		jakarta.persistence.criteria.Expression<Double> base = root.get("basePrice").as(Double.class);
		jakarta.persistence.criteria.Expression<Integer> discountRaw = root
				.<Integer>get("discountPercentage");
		jakarta.persistence.criteria.Expression<Double> discount = cb.coalesce(discountRaw, 0)
				.as(Double.class);
		jakarta.persistence.criteria.Expression<Double> fraction = cb.prod(discount, 0.01);
		jakarta.persistence.criteria.Expression<Double> factor = cb.diff(1.0, fraction);
		return cb.prod(base, factor);
	}

	public static Specification<Product> priceGte(Integer priceMin) {
		return (root, query, cb) -> priceMin == null
				? null
				: cb.greaterThanOrEqualTo(discountedPrice(root, cb), priceMin.doubleValue());
	}

	public static Specification<Product> priceLte(Integer priceMax) {
		return (root, query, cb) -> priceMax == null
				? null
				: cb.lessThanOrEqualTo(discountedPrice(root, cb), priceMax.doubleValue());
	}

	/** En oferta: mismo criterio que el badge OFERTA (descuento > 0) */
	public static Specification<Product> onSale(Boolean onSale) {
		return (root, query, cb) -> onSale == null || !onSale
				? null
				: cb.greaterThan(root.get("discountPercentage"), 0);
	}

	/** Novedad: mismo criterio que el badge NUEVO (creado hace < 30 días) */
	public static Specification<Product> isNew(Boolean isNew) {
		return (root, query, cb) -> isNew == null || !isNew
				? null
				: cb.greaterThanOrEqualTo(root.get("createdAt"),
						java.time.LocalDateTime.now().minusDays(30));
	}

	/** Búsqueda libre: nombre, descripción, marca, material, forma y categorías */
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
					cb.exists(categorySearch));
		};
	}

	public static Specification<Product> combine(List<String> categories, List<String> brands,
			List<String> materials, List<String> shapes, Integer priceMin, Integer priceMax, String sort,
			Boolean onSale, Boolean isNew, String q) {
		Specification<Product> filters = isActive().and(categoryIn(categories)).and(brandIn(brands))
				.and(materialIn(materials)).and(shapeIn(shapes)).and(priceGte(priceMin)).and(priceLte(priceMax))
				.and(onSale(onSale)).and(isNew(isNew)).and(textSearch(q));

		return (root, query, cb) -> {
			if (query.getResultType() != Long.class && query.getResultType() != long.class) {
				var price = discountedPrice(root, cb);
				var order = switch (sort == null ? "relevance" : sort) {
					case "price-asc" -> cb.asc(price);
					case "price-desc" -> cb.desc(price);
					default -> cb.desc(root.get("createdAt"));
				};
				query.orderBy(order);
			}
			return filters.toPredicate(root, query, cb);
		};
	}
}
