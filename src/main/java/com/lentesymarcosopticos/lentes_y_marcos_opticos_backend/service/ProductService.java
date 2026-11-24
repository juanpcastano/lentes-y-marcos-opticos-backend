package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductDetailDto.OtherColorDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductSummaryDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;

/**
 * ProductService — catálogo público. Una fila por SKU visible (activo y con stock).
 */
@Service
@AllArgsConstructor
public class ProductService {

	private static final int TOP_SELLERS_LIMIT = 10;
	private static final Duration NEW_BADGE_WINDOW = Duration.ofDays(30);

	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public PageResponse<ProductSummaryDto> getProducts(List<String> categories, List<String> brands,
			List<String> materials, List<String> shapes, List<String> colors, BigDecimal priceMin,
			BigDecimal priceMax, String sort, boolean includeOutOfStock, Boolean isNew, String q,
			int page, int size) {

		Specification<Product> spec = CatalogSpecifications.visible(includeOutOfStock)
				.and(CatalogSpecifications.categoryIn(categories))
				.and(CatalogSpecifications.brandIn(brands))
				.and(CatalogSpecifications.materialIn(materials))
				.and(CatalogSpecifications.shapeIn(shapes))
				.and(CatalogSpecifications.colorIn(colors))
				.and(CatalogSpecifications.priceGte(priceMin))
				.and(CatalogSpecifications.priceLte(priceMax))
				.and(CatalogSpecifications.isNew(isNew))
				.and(CatalogSpecifications.textSearch(q));

		Page<Product> result = productRepository.findAll(spec,
				PageRequest.of(page, size, CatalogSpecifications.sortFor(sort)));

		List<ProductSummaryDto> content = loadDetails(result.getContent()).stream()
				.map(this::toSummary)
				.toList();

		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ProductDetailDto getProductById(UUID id) {
		Product product = productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
		if (product.getStatus() == ProductStatus.HIDDEN || isSoftixMissing(product)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Product not found");
		}
		return toDetail(product);
	}

	@Transactional(readOnly = true)
	public List<ProductSummaryDto> getTopSellers() {
		// Sin ventas aún (F4) — placeholder: los productos más recientes
		Page<Product> result = productRepository.findAll(CatalogSpecifications.visible(false),
				PageRequest.of(0, TOP_SELLERS_LIMIT, CatalogSpecifications.sortFor(null)));
		return loadDetails(result.getContent()).stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public FacetsDto getFacets() {
		List<Object[]> bounds = productRepository.findPriceBounds(ProductStatus.ACTIVE);
		BigDecimal minPrice = null;
		BigDecimal maxPrice = null;
		if (!bounds.isEmpty() && bounds.get(0) != null) {
			Object[] row = bounds.get(0);
			minPrice = (BigDecimal) row[0];
			maxPrice = (BigDecimal) row[1];
		}
		return new FacetsDto(
				productRepository.findDistinctBrandNames(ProductStatus.ACTIVE),
				productRepository.findDistinctCategoryNames(ProductStatus.ACTIVE),
				productRepository.findDistinctMaterials(ProductStatus.ACTIVE),
				productRepository.findDistinctShapes(ProductStatus.ACTIVE),
				productRepository.findDistinctColors(ProductStatus.ACTIVE),
				minPrice, maxPrice);
	}

	/** Trae categorías e imágenes en una sola consulta y conserva el orden de la página. */
	private List<Product> loadDetails(List<Product> page) {
		if (page.isEmpty()) {
			return List.of();
		}
		List<UUID> ids = page.stream().map(Product::getId).toList();
		Map<UUID, Product> byId = productRepository.findAllWithDetailsByIdIn(ids).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		return ids.stream().map(byId::get).toList();
	}

	private ProductSummaryDto toSummary(Product p) {
		return new ProductSummaryDto(p.getId(), primaryImage(p), p.getName(), p.getColor(),
				brandName(p), p.getReference(), p.getPrice(), p.getStock(),
				p.getMaterial(), p.getShape(), categoryNames(p), badge(p));
	}

	private ProductDetailDto toDetail(Product p) {
		List<String> images = sortedImages(p);
		String imageUrl = images.isEmpty() ? null : images.get(0);
		List<String> additionalImages = images.stream().skip(1).toList();
		return new ProductDetailDto(p.getId(), p.getSku(), p.getName(), brandName(p),
				p.getReference(), p.getColor(), p.getPrice(), p.getStock(), isAvailable(p),
				p.getStatus(), imageUrl, additionalImages, p.getMaterial(), p.getShape(), categoryNames(p),
				badge(p), p.getDescription(), otherColors(p));
	}

	/** Mismo marca y referencia, otro SKU activo. Los agotados aparecen marcados como no disponibles. */
	private List<OtherColorDto> otherColors(Product p) {
		if (p.getBrand() == null || p.getReference() == null) {
			return List.of();
		}
		return productRepository.findByBrand_IdAndReferenceAndIdNot(p.getBrand().getId(),
						p.getReference(), p.getId()).stream()
				.filter(other -> other.getStatus() == ProductStatus.ACTIVE && !isSoftixMissing(other))
				.map(other -> new OtherColorDto(other.getId(), other.getColor(),
						primaryImage(other), isAvailable(other)))
				.toList();
	}

	private static boolean isAvailable(Product p) {
		return p.getStatus() == ProductStatus.ACTIVE && !isSoftixMissing(p)
				&& p.getStock() != null && p.getStock() > 0;
	}

	private static boolean isSoftixMissing(Product p) {
		return Boolean.TRUE.equals(p.getSoftixMissing());
	}

	private String primaryImage(Product p) {
		return sortedImages(p).stream().findFirst().orElse(null);
	}

	/** Imágenes ordenadas: primaria primero, luego por sortOrder (nulos al final). */
	private List<String> sortedImages(Product p) {
		if (p.getImages() == null) {
			return List.of();
		}
		return p.getImages().stream()
				.sorted(java.util.Comparator.comparing(ProductImage::getIsPrimary,
						java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
						.thenComparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder()))
				.map(ProductImage::getImageUrl)
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

	/** NUEVO si el producto tiene menos de 30 días (fecha de Softix si está disponible, si no la local). */
	private String badge(Product p) {
		LocalDateTime created = p.getSoftixCreatedAt() != null ? p.getSoftixCreatedAt() : p.getCreatedAt();
		if (created != null && Duration.between(created, LocalDateTime.now()).compareTo(NEW_BADGE_WINDOW) < 0) {
			return "NUEVO";
		}
		return null;
	}
}
