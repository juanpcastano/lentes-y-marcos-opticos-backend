package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductSummaryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.VariantDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.VariantImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductVariantRepository;

import lombok.AllArgsConstructor;

/**
 * ProductService — catálogo público.
 *
 * El catálogo muestra una fila por variante activa de producto activo
 * (producto + color): un producto multicolor aparece varias veces.
 */
@Service
@AllArgsConstructor
public class ProductService {

	private static final int TOP_SELLERS_LIMIT = 10;
	private static final Duration NEW_BADGE_WINDOW = Duration.ofDays(30);

	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;

	@Transactional(readOnly = true)
	public PageResponse<ProductSummaryDto> getProducts(List<String> categories, List<String> brands,
			List<String> materials, List<String> shapes, List<String> colors, Integer priceMin,
			Integer priceMax, String sort, Boolean onSale, Boolean isNew, String q, int page, int size) {

		Pageable pageable = PageRequest.of(page, size);
		var spec = VariantSpecifications.combine(categories, brands, materials, shapes, colors,
				priceMin, priceMax, sort, onSale, isNew, q);
		Page<ProductVariant> result = variantRepository.findAll(spec, pageable);

		Map<UUID, ProductVariant> details = loadVariantDetails(
				result.getContent().stream().map(ProductVariant::getId).toList());

		List<ProductSummaryDto> content = result.getContent().stream()
				.map(v -> details.getOrDefault(v.getId(), v))
				.map(v -> toSummary(v.getProduct(), v))
				.toList();

		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ProductDetailDto getProductById(UUID id) {
		return getProductById(id, null);
	}

	@Transactional(readOnly = true)
	public ProductDetailDto getProductById(UUID id, UUID variantId) {
		Product product = productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
		return toDetail(product, variantId);
	}

	@Transactional(readOnly = true)
	public List<ProductSummaryDto> getTopSellers() {
		// Sin ventas aún (F4) — placeholder: las variantes más recientes
		Page<ProductVariant> result = variantRepository.findAll(VariantSpecifications.combine(
				null, null, null, null, null, null, null, "relevance", null, null, null),
				PageRequest.of(0, TOP_SELLERS_LIMIT));
		Map<UUID, ProductVariant> details = loadVariantDetails(
				result.getContent().stream().map(ProductVariant::getId).toList());
		return result.getContent().stream()
				.map(v -> details.getOrDefault(v.getId(), v))
				.map(v -> toSummary(v.getProduct(), v))
				.toList();
	}

	@Transactional(readOnly = true)
	public FacetsDto getFacets() {
		List<String> materials = productRepository.findDistinctMaterials();
		List<String> shapes = productRepository.findDistinctShapes();
		List<String> colors = productRepository.findDistinctColors();
		List<Object[]> bounds = productRepository.findPriceBounds();
		Integer minPrice = null;
		Integer maxPrice = null;
		if (!bounds.isEmpty() && bounds.get(0) != null) {
			Object[] row = bounds.get(0);
			if (row[0] != null) {
				minPrice = (int) Math.round(((Number) row[0]).doubleValue());
			}
			if (row[1] != null) {
				maxPrice = (int) Math.round(((Number) row[1]).doubleValue());
			}
		}
		return new FacetsDto(materials, shapes, colors, minPrice, maxPrice);
	}

	/**
	 * Trae producto + marca + categorías + imágenes de una página de variantes
	 * en una sola query (evita N+1). Devuelve variante-id → variante.
	 */
	private Map<UUID, ProductVariant> loadVariantDetails(List<UUID> variantIds) {
		if (variantIds.isEmpty()) {
			return Map.of();
		}
		List<UUID> productIds = variantRepository.findAllById(variantIds).stream()
				.map(v -> v.getProduct().getId())
				.distinct()
				.toList();
		return productRepository.findAllWithDetailsByIdIn(productIds).stream()
				.flatMap(p -> p.getVariants().stream())
				.collect(Collectors.toMap(ProductVariant::getId, Function.identity(),
						(a, b) -> a));
	}

	private ProductSummaryDto toSummary(Product p, ProductVariant v) {
		int discount = discountOf(v);
		return new ProductSummaryDto(v.getId(), p.getId(), primaryImage(v), p.getName(),
				v.getColor(), brandName(p), finalPrice(v), v.getPrice(), discount,
				p.getMaterial(), p.getShape(), categoryNames(p), badge(p, v));
	}

	private ProductDetailDto toDetail(Product p, UUID variantId) {
		List<VariantDto> variants = p.getVariants() == null ? List.of()
				: p.getVariants().stream()
						.sorted(Comparator.comparing(ProductVariant::getColor,
								Comparator.nullsLast(String::compareToIgnoreCase)))
						.map(this::toVariant).toList();
		ProductVariant selected = selectVariant(p, variantId);
		VariantDto selectedDto = selected != null ? toVariant(selected) : null;
		String imageUrl = selected != null ? primaryImage(selected) : null;
		List<String> additionalImages = selected != null
				? sortedImages(selected).stream()
						.filter(img -> img != null && !img.equals(imageUrl))
						.toList()
				: List.of();
		int price = selected != null ? selected.getPrice() : 0;
		int discount = selected != null ? discountOf(selected) : 0;
		int discounted = selected != null ? finalPrice(selected) : 0;
		return new ProductDetailDto(p.getId(), p.getId(), selected != null ? selected.getId() : null,
				imageUrl, p.getName(), selected != null ? selected.getColor() : null,
				brandName(p), discounted, p.getMaterial(), p.getShape(), categoryNames(p),
				selected != null ? badge(p, selected) : badge(p, null),
				additionalImages, price, discounted, discount, p.getDescription(),
				variants, selectedDto);
	}

	/** Variante pedida si pertenece al producto; si no, la primera activa. */
	private ProductVariant selectVariant(Product p, UUID variantId) {
		if (p.getVariants() == null || p.getVariants().isEmpty()) {
			return null;
		}
		if (variantId != null) {
			return p.getVariants().stream()
					.filter(v -> variantId.equals(v.getId()))
					.findFirst()
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
							"Variant not found for this product"));
		}
		return p.getVariants().stream()
				.filter(v -> Boolean.TRUE.equals(v.getIsActive()))
				.sorted(Comparator.comparing(ProductVariant::getColor,
						Comparator.nullsLast(String::compareToIgnoreCase)))
				.findFirst()
				.orElseGet(() -> p.getVariants().stream().findFirst().orElse(null));
	}

	static int finalPrice(ProductVariant v) {
		int discount = discountOf(v);
		return v.getPrice() - (int) Math.round(v.getPrice() * discount / 100.0);
	}

	private static int discountOf(ProductVariant v) {
		return v.getDiscountPercentage() != null ? v.getDiscountPercentage() : 0;
	}

	private VariantDto toVariant(ProductVariant v) {
		return new VariantDto(v.getId(), v.getColor(), v.getSku(),
				v.getPrice(), v.getDiscountPercentage(), finalPrice(v), v.getIsActive());
	}

	private String primaryImage(ProductVariant v) {
		if (v.getImages() == null || v.getImages().isEmpty()) {
			return null;
		}
		return sortedImages(v).stream().findFirst().orElse(null);
	}

	/**
	 * Imágenes ordenadas: primaria primero, luego por sortOrder (nulos al final)
	 */
	private List<String> sortedImages(ProductVariant v) {
		if (v.getImages() == null) {
			return List.of();
		}
		return v.getImages().stream()
				.sorted(Comparator.comparing(VariantImage::getIsPrimary, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder()))
				.map(VariantImage::getImageUrl)
				.toList();
	}

	private String brandName(Product p) {
		Brand brand = p.getBrand();
		return brand != null ? brand.getName() : null;
	}

	private List<String> categoryNames(Product p) {
		if (p.getCategories() == null) {
			return List.of();
		}
		return p.getCategories().stream().map(Category::getName).sorted().toList();
	}

	/** OFERTA si la variante tiene descuento (precede); NUEVO si < 30 días; si no, null */
	private String badge(Product p, ProductVariant v) {
		if (v != null && v.getDiscountPercentage() != null && v.getDiscountPercentage() > 0) {
			return "OFERTA";
		}
		LocalDateTime createdAt = p.getCreatedAt();
		if (createdAt != null && Duration.between(createdAt, LocalDateTime.now()).compareTo(NEW_BADGE_WINDOW) < 0) {
			return "NUEVO";
		}
		return null;
	}
}
