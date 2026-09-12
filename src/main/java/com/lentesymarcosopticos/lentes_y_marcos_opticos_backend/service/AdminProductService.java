package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductUpsertRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.VariantDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;

/**
 * AdminProductService — CRUD de productos desde el panel de administración
 */
@Service
@AllArgsConstructor
public class AdminProductService {

	private final ProductRepository productRepository;
	private final CategoryRepository categoryRepository;
	private final BrandRepository brandRepository;
	private final StorageService storageService;

	@Transactional(readOnly = true)
	public PageResponse<AdminProductDto> list(String q, List<String> brands, List<String> categories,
			List<String> materials, List<String> shapes, Integer priceMin, Integer priceMax,
			Boolean active, String sort, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<Product> result = productRepository.findAll((root, query, cb) -> {
			if (query.getResultType() != Long.class && query.getResultType() != long.class) {
				query.orderBy(ordersFor(root, cb, sort));
			}
			Predicate predicate = cb.conjunction();
			if (q != null && !q.isBlank()) {
				String search = "%" + q.toLowerCase().trim() + "%";
				var categorySearch = query.subquery(Integer.class);
				var categoryRoot = categorySearch.from(Product.class);
				var categoryJoin = categoryRoot.join("categories");
				categorySearch.select(cb.literal(1)).where(
						cb.equal(categoryRoot.get("id"), root.get("id")),
						cb.like(cb.lower(categoryJoin.get("name")), search));
				predicate = cb.and(predicate,
						cb.or(
								cb.like(cb.lower(root.get("name")), search),
								cb.like(cb.lower(root.get("description")), search),
								cb.like(cb.lower(root.get("brand").get("name")), search),
								cb.like(cb.lower(root.get("material")), search),
								cb.exists(categorySearch)));
			}
			if (active != null) {
				predicate = cb.and(predicate, cb.equal(root.get("isActive"), active));
			}
			if (brands != null && !brands.isEmpty()) {
				predicate = cb.and(predicate, root.get("brand").get("name").in(brands));
			}
			if (materials != null && !materials.isEmpty()) {
				predicate = cb.and(predicate, root.get("material").in(materials));
			}
			if (shapes != null && !shapes.isEmpty()) {
				predicate = cb.and(predicate, root.get("shape").in(shapes));
			}
			if (priceMin != null) {
				predicate = cb.and(predicate,
						cb.greaterThanOrEqualTo(ProductSpecifications.discountedPrice(root, cb), priceMin.doubleValue()));
			}
			if (priceMax != null) {
				predicate = cb.and(predicate,
						cb.lessThanOrEqualTo(ProductSpecifications.discountedPrice(root, cb), priceMax.doubleValue()));
			}
			if (categories != null && !categories.isEmpty()) {
				var subquery = query.subquery(Integer.class);
				var subRoot = subquery.from(Product.class);
				var categoryJoin = subRoot.join("categories");
				subquery.select(cb.literal(1))
						.where(cb.equal(subRoot.get("id"), root.get("id")),
								categoryJoin.get("name").in(categories));
				predicate = cb.and(predicate, cb.exists(subquery));
			}
			return predicate;
		}, pageable);

		List<UUID> ids = result.getContent().stream().map(Product::getId).toList();
		Map<UUID, Product> details = productRepository.findAllWithDetailsByIdIn(ids).stream()
				.collect(Collectors.toMap(Product::getId, Function.identity()));

		List<AdminProductDto> content = result.getContent().stream()
				.map(p -> details.getOrDefault(p.getId(), p))
				.map(this::toDto)
				.toList();

		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public FacetsDto facets() {
		Object[] row = (Object[]) productRepository.findAllPriceBounds()[0];
		Integer minPrice = row != null ? (int) Math.round(((Number) row[0]).doubleValue()) : null;
		Integer maxPrice = row != null ? (int) Math.round(((Number) row[1]).doubleValue()) : null;
		return new FacetsDto(productRepository.findAllDistinctMaterials(),
				productRepository.findAllDistinctShapes(), minPrice, maxPrice);
	}

	@Transactional(readOnly = true)
	public AdminProductDto get(UUID id) {
		return toDto(loadDetail(id));
	}

	@Transactional
	public AdminProductDto create(ProductUpsertRequest request) {
		Product product = new Product();
		product.setName(request.name().trim());
		product.setBasePrice(request.basePrice());
		product.setDiscountPercentage(request.discountPercentage());
		product.setMaterial(trimOrNull(request.material()));
		product.setShape(trimOrNull(request.shape()));
		product.setDescription(request.description());
		product.setTaxRate(request.taxRate());
		product.setProductType(request.productType().trim());
		product.setIsActive(request.isActive() == null || request.isActive());
		product.setBrand(resolveBrand(request.brandId()));
		product.setCategories(new HashSet<>(resolveCategories(request.categories())));

		Product saved = productRepository.save(product);
		if (request.variants() == null || request.variants().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El producto debe tener al menos una variante");
		}
		validateVariants(request.variants());
		checkSkus(request.variants(), null);
		for (var vr : request.variants()) {
			ProductVariant variant = new ProductVariant();
			variant.setProduct(saved);
			applyVariant(variant, vr);
			saved.getVariants().add(variant);
		}
		productRepository.saveAndFlush(saved);
		return toDto(saved);
	}

	@Transactional
	public AdminProductDto update(UUID id, ProductUpsertRequest request) {
		Product product = loadDetail(id);

		if (request.name() != null && !request.name().isBlank()) {
			product.setName(request.name().trim());
		}
		if (request.basePrice() != null) {
			product.setBasePrice(request.basePrice());
		}
		if (request.discountPercentage() != null) {
			product.setDiscountPercentage(request.discountPercentage());
		}
		if (request.material() != null) {
			product.setMaterial(trimOrNull(request.material()));
		}
		if (request.shape() != null) {
			product.setShape(trimOrNull(request.shape()));
		}
		if (request.description() != null) {
			product.setDescription(request.description());
		}
		if (request.taxRate() != null) {
			product.setTaxRate(request.taxRate());
		}
		if (request.productType() != null && !request.productType().isBlank()) {
			product.setProductType(request.productType().trim());
		}
		if (request.isActive() != null) {
			product.setIsActive(request.isActive());
		}
		if (request.brandId() != null) {
			product.setBrand(resolveBrand(request.brandId()));
		}
		if (request.categories() != null) {
			product.setCategories(new HashSet<>(resolveCategories(request.categories())));
		}
		if (request.variants() != null) {
			if (request.variants().isEmpty()) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"El producto debe tener al menos una variante");
			}
			validateVariants(request.variants());
			reconcileVariants(product, request.variants());
			productRepository.saveAndFlush(product);
		}
		return toDto(product);
	}

	/** Soft delete: is_active = false */
	@Transactional
	public void deactivate(UUID id) {
		setActive(id, false);
	}

	@Transactional
	public void setActive(UUID id, boolean active) {
		Product product = productRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
		product.setIsActive(active);
	}

	// ---------- imágenes ----------

	@Transactional
	public ProductImageDto addImage(UUID productId, MultipartFile file, Boolean primary) {
		Product product = loadDetail(productId);
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
		}
		String url = storageService.store(file, "products/" + productId);

		boolean asPrimary = Boolean.TRUE.equals(primary) || product.getImages().isEmpty();
		if (asPrimary) {
			product.getImages().forEach(img -> img.setIsPrimary(false));
		}
		int nextSort = product.getImages().stream()
				.map(ProductImage::getSortOrder)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(0) + 1;

		ProductImage image = new ProductImage();
		image.setProduct(product);
		image.setImageUrl(url);
		image.setIsPrimary(asPrimary);
		image.setSortOrder(nextSort);
		product.getImages().add(image);
		return toImageDto(image);
	}

	@Transactional
	public void deleteImage(UUID productId, UUID imageId) {
		Product product = loadDetail(productId);
		ProductImage image = product.getImages().stream()
				.filter(img -> img.getId().equals(imageId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
		product.getImages().remove(image);
		storageService.delete(image.getImageUrl());
	}

	@Transactional
	public ProductImageDto setPrimaryImage(UUID productId, UUID imageId) {
		Product product = loadDetail(productId);
		ProductImage target = product.getImages().stream()
				.filter(img -> img.getId().equals(imageId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
		product.getImages().forEach(img -> img.setIsPrimary(false));
		target.setIsPrimary(true);
		return toImageDto(target);
	}

	// ---------- helpers ----------

	private List<Order> ordersFor(Root<Product> root, CriteriaBuilder cb, String sort) {
		if (sort == null || sort.isBlank()) {
			return List.of(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
		}
		String[] parts = sort.split(",");
		String field = parts[0].trim();
		boolean desc = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc");
		Order primary;
		switch (field) {
			case "name" -> primary = desc
					? cb.desc(cb.lower(root.get("name")))
					: cb.asc(cb.lower(root.get("name")));
			case "brand" -> {
				Join<Product, Brand> brand = root.join("brand", JoinType.LEFT);
				primary = desc
						? cb.desc(cb.lower(brand.<String>get("name")))
						: cb.asc(cb.lower(brand.<String>get("name")));
			}
			case "basePrice" -> primary = desc
					? cb.desc(root.get("basePrice"))
					: cb.asc(root.get("basePrice"));
			case "isActive" -> primary = desc
					? cb.desc(root.get("isActive"))
					: cb.asc(root.get("isActive"));
			case "createdAt" -> primary = desc
					? cb.desc(root.get("createdAt"))
					: cb.asc(root.get("createdAt"));
			case "updatedAt" -> primary = desc
					? cb.desc(root.get("updatedAt"))
					: cb.asc(root.get("updatedAt"));
			default -> primary = cb.desc(root.get("createdAt"));
		}
		return List.of(primary, cb.desc(root.get("id")));
	}

	private Product loadDetail(UUID id) {
		return productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
	}

	private Brand resolveBrand(UUID brandId) {
		if (brandId == null) {
			return null;
		}
		return brandRepository.findById(brandId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Marca no encontrada"));
	}

	private List<Category> resolveCategories(List<String> names) {
		if (names == null) {
			return List.of();
		}
		List<String> clean = names.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
		if (clean.isEmpty()) {
			return List.of();
		}
		List<Category> found = categoryRepository.findByNameIn(clean);
		if (found.size() != clean.size()) {
			var foundNames = found.stream().map(Category::getName).collect(Collectors.toSet());
			List<String> missing = clean.stream().filter(n -> !foundNames.contains(n)).toList();
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Categorías no encontradas: " + String.join(", ", missing));
		}
		return found;
	}

	private void reconcileVariants(Product product, List<ProductUpsertRequest.VariantRequest> requests) {
		checkSkus(requests, product.getId());
		Map<UUID, ProductVariant> existing = product.getVariants().stream()
				.collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
		Set<UUID> kept = new HashSet<>();

		for (var vr : requests) {
			if (vr.id() == null) {
				ProductVariant variant = new ProductVariant();
				variant.setProduct(product);
				applyVariant(variant, vr);
				product.getVariants().add(variant);
			} else {
				ProductVariant variant = existing.get(vr.id());
				if (variant == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "Variante no encontrada: " + vr.id());
				}
				applyVariant(variant, vr);
				kept.add(vr.id());
			}
		}
		product.getVariants().removeIf(v -> v.getId() != null && !kept.contains(v.getId()));
	}

	private void applyVariant(ProductVariant variant, ProductUpsertRequest.VariantRequest vr) {
		variant.setVariantName(trimOrNull(vr.variantName()));
		variant.setSku(trimOrNull(vr.sku()));
		variant.setImageUrl(trimOrNull(vr.imageUrl()));
		variant.setIsActive(vr.isActive() == null || vr.isActive());
	}

	private void validateVariants(List<ProductUpsertRequest.VariantRequest> variants) {
		Set<String> names = new HashSet<>();
		for (var variant : variants) {
			String name = trimOrNull(variant.variantName());
			if (name == null
					|| trimOrNull(variant.sku()) == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Cada variante debe tener nombre y SKU");
			}
			if (!names.add(name.toLowerCase(Locale.ROOT))) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"No puede haber variantes con el mismo nombre");
			}
		}
	}

	private void checkSkus(List<ProductUpsertRequest.VariantRequest> variants, UUID productId) {
		UUID sentinel = productId != null ? productId : UUID.randomUUID();
		List<String> skus = variants.stream()
				.map(ProductUpsertRequest.VariantRequest::sku)
				.map(this::trimOrNull)
				.filter(Objects::nonNull)
				.toList();
		if (skus.size() != new HashSet<>(skus).size()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SKUs duplicados en el request");
		}
		for (String sku : skus) {
			if (productRepository.countOtherVariantsBySku(sku, sentinel) > 0) {
				throw new ApiException(HttpStatus.CONFLICT, "El SKU ya está en uso: " + sku);
			}
		}
	}

	private String trimOrNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private ProductImageDto toImageDto(ProductImage img) {
		return new ProductImageDto(img.getId(), img.getImageUrl(), img.getIsPrimary(), img.getSortOrder());
	}

	private VariantDto toVariantDto(ProductVariant v) {
		return new VariantDto(v.getId(), v.getVariantName(), v.getSku(),
				v.getImageUrl(), v.getIsActive());
	}

	private AdminProductDto toDto(Product p) {
		Brand brand = p.getBrand();
		List<ProductImageDto> images = p.getImages() == null ? List.of()
				: p.getImages().stream()
						.sorted(Comparator
								.comparing(ProductImage::getIsPrimary,
										Comparator.nullsLast(Comparator.reverseOrder()))
								.thenComparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE
										: img.getSortOrder()))
						.map(this::toImageDto)
						.toList();
		List<VariantDto> variants = p.getVariants() == null ? List.of()
				: p.getVariants().stream().sorted(Comparator
						.comparing(ProductVariant::getId, Comparator.nullsLast(Comparator.naturalOrder())))
						.map(this::toVariantDto).toList();
		List<String> categories = p.getCategories() == null ? List.of()
				: p.getCategories().stream().map(Category::getName).sorted().toList();

		return new AdminProductDto(p.getId(), p.getName(),
				brand != null ? brand.getId() : null, brand != null ? brand.getName() : null,
				p.getBasePrice(), p.getDiscountPercentage(), p.getMaterial(), p.getShape(),
				p.getDescription(), p.getTaxRate(), p.getProductType(), p.getIsActive(),
				p.getCreatedAt(), p.getUpdatedAt(), categories, images, variants);
	}
}
