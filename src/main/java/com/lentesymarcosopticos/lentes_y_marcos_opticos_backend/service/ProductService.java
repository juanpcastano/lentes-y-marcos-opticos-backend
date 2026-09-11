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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;

/**
 * ProductService
 */
@Service
@AllArgsConstructor
public class ProductService {

	private static final int TOP_SELLERS_LIMIT = 10;
	private static final Duration NEW_BADGE_WINDOW = Duration.ofDays(30);

	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public PageResponse<ProductSummaryDto> getProducts(List<String> categories, List<String> brands,
			List<String> materials, List<String> shapes, Integer priceMin, Integer priceMax, String sort,
			int page, int size) {

		Pageable pageable = PageRequest.of(page, size);
		var spec = ProductSpecifications.combine(categories, brands, materials, shapes, priceMin, priceMax, sort);
		Page<Product> result = productRepository.findAll(spec, pageable);

		// Segunda consulta para traer imágenes/categorías/variantes de la página en
		// una sola query (evita N+1)
		List<UUID> ids = result.getContent().stream().map(Product::getId).toList();
		Map<UUID, Product> details = productRepository.findAllWithDetailsByIdIn(ids).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		List<ProductSummaryDto> content = result.getContent().stream()
				.map(p -> details.getOrDefault(p.getId(), p))
				.map(this::toSummary)
				.toList();

		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ProductDetailDto getProductById(UUID id) {
		Product product = productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
		return toDetail(product);
	}

	@Transactional(readOnly = true)
	public List<ProductSummaryDto> getTopSellers() {
		// Sin ventas aún (F4) — placeholder: los más recientes
		Page<Product> result = productRepository.findAll(ProductSpecifications.combine(null, null, null, null,
				null, null, "relevance"), PageRequest.of(0, TOP_SELLERS_LIMIT));
		List<UUID> ids = result.getContent().stream().map(Product::getId).toList();
		Map<UUID, Product> details = productRepository.findAllWithDetailsByIdIn(ids).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));
		return result.getContent().stream()
				.map(p -> details.getOrDefault(p.getId(), p))
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public FacetsDto getFacets() {
		List<String> materials = productRepository.findDistinctMaterials();
		List<String> shapes = productRepository.findDistinctShapes();
		Object[] row = (Object[]) productRepository.findPriceBounds()[0];
		Integer minPrice = row != null ? (int) Math.round(((Number) row[0]).doubleValue()) : null;
		Integer maxPrice = row != null ? (int) Math.round(((Number) row[1]).doubleValue()) : null;
		return new FacetsDto(materials, shapes, minPrice, maxPrice);
	}

	private ProductSummaryDto toSummary(Product p) {
		return new ProductSummaryDto(p.getId(), primaryImage(p), p.getName(), brandName(p),
				finalPrice(p), p.getMaterial(), p.getShape(), categoryNames(p), badge(p));
	}

	private ProductDetailDto toDetail(Product p) {
		List<String> additionalImages = sortedImages(p).stream()
				.filter(img -> img != null && !img.equals(primaryImage(p)))
				.toList();
		int discount = p.getDiscountPercentage() != null ? p.getDiscountPercentage() : 0;
		int discounted = p.getBasePrice() - (int) Math.round(p.getBasePrice() * discount / 100.0);
		List<VariantDto> variants = p.getVariants() == null ? List.of()
				: p.getVariants().stream().map(this::toVariant).toList();
		return new ProductDetailDto(p.getId(), primaryImage(p), p.getName(), brandName(p),
				discounted, p.getMaterial(), p.getShape(), categoryNames(p), badge(p),
				additionalImages, p.getBasePrice(), discounted, discount, p.getDescription(),
				variants, p.getIsActive());
	}

	private int finalPrice(Product p) {
		int discount = p.getDiscountPercentage() != null ? p.getDiscountPercentage() : 0;
		return p.getBasePrice() - (int) Math.round(p.getBasePrice() * discount / 100.0);
	}

	private VariantDto toVariant(ProductVariant v) {
		return new VariantDto(v.getId(), v.getVariantName(), v.getSku(),
				v.getPriceAdjustment(), v.getImageUrl(), v.getIsActive());
	}

	private String primaryImage(Product p) {
		if (p.getImages() == null || p.getImages().isEmpty()) {
			return null;
		}
		return sortedImages(p).stream().findFirst().orElse(null);
	}

	/**
	 * Imágenes ordenadas: primaria primero, luego por sortOrder (nulos al final)
	 */
	private List<String> sortedImages(Product p) {
		if (p.getImages() == null) {
			return List.of();
		}
		return p.getImages().stream()
				.sorted(Comparator.comparing(ProductImage::getIsPrimary, Comparator.nullsLast(Comparator.reverseOrder()))
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

	/** OFERTA si hay descuento (precede); NUEVO si < 30 días; si no, null */
	private String badge(Product p) {
		if (p.getDiscountPercentage() != null && p.getDiscountPercentage() > 0) {
			return "OFERTA";
		}
		LocalDateTime createdAt = p.getCreatedAt();
		if (createdAt != null && Duration.between(createdAt, LocalDateTime.now()).compareTo(NEW_BADGE_WINDOW) < 0) {
			return "NUEVO";
		}
		return null;
	}
}
