package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductDto.AdminVariantDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.FacetsDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductUpsertRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.VariantImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductVariantRepository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.AllArgsConstructor;

/**
 * AdminProductService — CRUD de productos desde el panel de administración.
 * El precio, el descuento y las imágenes viven en la variante (color).
 */
@Service
@AllArgsConstructor
public class AdminProductService {

	private final ProductRepository productRepository;
	private final ProductVariantRepository variantRepository;
	private final CategoryRepository categoryRepository;
	private final BrandRepository brandRepository;
	private final StorageService storageService;

	@Transactional(readOnly = true)
	public PageResponse<AdminProductDto> list(String q, List<String> brands, List<String> categories,
			List<String> materials, List<String> shapes, List<String> colors, Integer priceMin,
			Integer priceMax, Boolean onSale, Boolean isNew, Boolean active, Boolean needsReview,
			String sort, int page, int size) {
		Pageable pageable = PageRequest.of(page, size);
		Page<Product> result = productRepository.findAll((root, query, cb) -> {
			if (query.getResultType() != Long.class && query.getResultType() != long.class) {
				query.orderBy(ordersFor(root, cb, sort));
			}
			Predicate predicate = cb.conjunction();
			if (q != null && !q.isBlank()) {
				predicate = cb.and(predicate,
						ProductSpecifications.textSearch(q).toPredicate(root, query, cb));
			}
			if (active != null) {
				predicate = cb.and(predicate,
						ProductSpecifications.hasActiveVariant(active).toPredicate(root, query, cb));
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
			if (colors != null && !colors.isEmpty()) {
				predicate = cb.and(predicate,
						ProductSpecifications.colorIn(colors).toPredicate(root, query, cb));
			}
			if (priceMin != null) {
				predicate = cb.and(predicate,
						ProductSpecifications.priceGte(priceMin).toPredicate(root, query, cb));
			}
			if (priceMax != null) {
				predicate = cb.and(predicate,
						ProductSpecifications.priceLte(priceMax).toPredicate(root, query, cb));
			}
			if (onSale != null && onSale) {
				predicate = cb.and(predicate,
						ProductSpecifications.onSale(true).toPredicate(root, query, cb));
			}
			if (isNew != null && isNew) {
				predicate = cb.and(predicate,
						ProductSpecifications.isNew(true).toPredicate(root, query, cb));
			}
			if (categories != null && !categories.isEmpty()) {
				predicate = cb.and(predicate,
						ProductSpecifications.categoryIn(categories).toPredicate(root, query, cb));
			}
			if (needsReview != null && needsReview) {
				Subquery<Long> variantSq = query.subquery(Long.class);
				Root<ProductVariant> variant = variantSq.from(ProductVariant.class);
				variantSq.select(cb.literal(1L));
				variantSq.where(cb.and(
						cb.equal(variant.get("product").get("id"), root.get("id")),
						cb.or(
								cb.equal(cb.lower(variant.get("color")), "varios"),
								cb.isEmpty(variant.get("images")))));
				predicate = cb.and(predicate, cb.or(
						cb.isNull(root.get("productType")),
						cb.equal(root.get("productType"), ""),
						cb.isNull(root.get("material")),
						cb.equal(root.get("material"), ""),
						cb.isNull(root.get("shape")),
						cb.equal(root.get("shape"), ""),
						cb.isEmpty(root.get("categories")),
						cb.isEmpty(root.get("variants")),
						cb.exists(variantSq)));
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
		return new FacetsDto(productRepository.findDistinctMaterials(),
				productRepository.findDistinctShapes(), productRepository.findDistinctColors(),
				minPrice, maxPrice);
	}

	@Transactional(readOnly = true)
	public AdminProductDto get(UUID id) {
		return toDto(loadDetail(id));
	}

	@Transactional
	public AdminProductDto create(ProductUpsertRequest request) {
		Product product = new Product();
		product.setName(request.name().trim());
		product.setMaterial(trimOrNull(request.material()));
		product.setShape(trimOrNull(request.shape()));
		product.setDescription(request.description());
		product.setProductType(request.productType().trim());
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
			attachStagedImages(variant, vr.images());
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
		if (request.material() != null) {
			product.setMaterial(trimOrNull(request.material()));
		}
		if (request.shape() != null) {
			product.setShape(trimOrNull(request.shape()));
		}
		if (request.description() != null) {
			product.setDescription(request.description());
		}
		if (request.productType() != null && !request.productType().isBlank()) {
			product.setProductType(request.productType().trim());
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

	/**
	 * Hard delete: elimina el producto con sus variantes e imágenes (DB). Las
	 * imágenes en S3 quedan huérfanas y se purgan desde /admin/gallery.
	 */
	@Transactional
	public void deleteProduct(UUID id) {
		Product product = loadDetail(id);
		productRepository.delete(product);
	}

	/**
	 * Activar/desactivar en lote: fija isActive en todas las variantes de los
	 * productos indicados. Desactivar oculta el producto de la tienda sin
	 * borrarlo.
	 *
	 * @return número de variantes actualizadas
	 */
	@Transactional
	public int bulkSetVariantsActive(List<UUID> ids, boolean active) {
		if (ids == null || ids.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Debes seleccionar al menos un producto");
		}
		List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinct.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Debes seleccionar al menos un producto");
		}
		return variantRepository.updateActiveByProductIds(distinct, active);
	}

	// ---------- imágenes de variante ----------

	@Transactional
	public ProductImageDto addImage(UUID variantId, MultipartFile file, Boolean primary) {
		ProductVariant variant = loadVariant(variantId);
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
		}
		String url = storageService.store(file, "variants/" + variantId);
		return addImageUrl(variant, url, primary);
	}

	@Transactional
	public ProductImageDto addExistingImage(UUID variantId, String url, Boolean primary) {
		ProductVariant variant = loadVariant(variantId);
		String key = storageService.keyForUrl(url);
		if (key == null || key.contains("..") || !key.startsWith("variants/")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen no pertenece al almacenamiento de variantes");
		}
		boolean exists = storageService.list("variants").stream()
				.anyMatch(object -> object.key().equals(key));
		if (!exists) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen seleccionada ya no existe");
		}
		return addImageUrl(variant, url, primary);
	}

	private ProductImageDto addImageUrl(ProductVariant variant, String url, Boolean primary) {
		if (variant.getImages() == null) {
			variant.setImages(new LinkedHashSet<>());
		}
		boolean asPrimary = Boolean.TRUE.equals(primary) || variant.getImages().isEmpty();
		if (asPrimary) {
			variant.getImages().forEach(img -> img.setIsPrimary(false));
		}
		int nextSort = variant.getImages().stream()
				.map(VariantImage::getSortOrder)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(0) + 1;

		VariantImage image = new VariantImage();
		image.setVariant(variant);
		image.setImageUrl(url);
		image.setIsPrimary(asPrimary);
		image.setSortOrder(nextSort);
		variant.getImages().add(image);
		return toImageDto(image);
	}

	@Transactional
	public void deleteImage(UUID variantId, UUID imageId) {
		ProductVariant variant = loadVariant(variantId);
		VariantImage image = variant.getImages().stream()
				.filter(img -> img.getId().equals(imageId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
		boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
		variant.getImages().remove(image);
		if (wasPrimary) {
			variant.getImages().stream()
					.min(Comparator.comparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder()))
					.ifPresent(next -> next.setIsPrimary(true));
		}
	}

	@Transactional
	public ProductImageDto setPrimaryImage(UUID variantId, UUID imageId) {
		ProductVariant variant = loadVariant(variantId);
		VariantImage target = variant.getImages().stream()
				.filter(img -> img.getId().equals(imageId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
		variant.getImages().forEach(img -> img.setIsPrimary(false));
		target.setIsPrimary(true);
		return toImageDto(target);
	}

	@Transactional
	public List<ProductImageDto> reorderImages(UUID variantId, List<UUID> imageIds) {
		ProductVariant variant = loadVariant(variantId);
		List<VariantImage> images = variant.getImages().stream().toList();
		if (images.isEmpty() && (imageIds == null || imageIds.isEmpty())) {
			return List.of();
		}
		if (imageIds == null || imageIds.size() != images.size()
				|| imageIds.size() != new LinkedHashSet<>(imageIds).size()
				|| images.stream().map(VariantImage::getId).anyMatch(id -> !imageIds.contains(id))) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El orden debe incluir exactamente todas las imágenes de la variante");
		}

		Map<UUID, VariantImage> byId = images.stream()
				.collect(Collectors.toMap(VariantImage::getId, Function.identity()));
		for (int index = 0; index < imageIds.size(); index++) {
			byId.get(imageIds.get(index)).setSortOrder(index + 1);
		}
		VariantImage first = byId.get(imageIds.get(0));
		variant.getImages().forEach(img -> img.setIsPrimary(img == first));
		return imageIds.stream().map(byId::get).map(this::toImageDto).toList();
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
			case "createdAt" -> primary = desc
					? cb.desc(root.get("createdAt"))
					: cb.asc(root.get("createdAt"));
			case "updatedAt" -> primary = desc
					? cb.desc(root.get("updatedAt"))
					: cb.asc(root.get("updatedAt"));
			// Sin precio único a nivel producto: el orden por precio vive en el
			// catálogo (por variante). Se degrada a novedad.
			default -> primary = cb.desc(root.get("createdAt"));
		}
		return List.of(primary, cb.desc(root.get("id")));
	}

	private Product loadDetail(UUID id) {
		return productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
	}

	private ProductVariant loadVariant(UUID variantId) {
		return variantRepository.findById(variantId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variante no encontrada"));
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
				attachStagedImages(variant, vr.images());
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
		variant.setColor(trimOrNull(vr.color()));
		variant.setSku(trimOrNull(vr.sku()));
		variant.setPrice(vr.price());
		variant.setDiscountPercentage(vr.discountPercentage());
		variant.setIsActive(vr.isActive() == null || vr.isActive());
	}

	/**
	 * Crea las filas VariantImage de una variante nueva a partir de URLs ya
	 * subidas a `variants/` (staging sin referencias). Valida pertenencia al
	 * bucket, existencia y duplicados; la primera es la principal salvo que
	 * el request marque otra. Las imágenes de variantes existentes se
	 * gestionan por endpoints dedicados y aquí se ignoran.
	 */
	private void attachStagedImages(ProductVariant variant,
			List<ProductUpsertRequest.VariantImageRequest> images) {
		if (images == null || images.isEmpty()) {
			return;
		}
		Set<String> storedKeys = storageService.list("variants").stream()
				.map(StorageService.StoredObject::key)
				.collect(Collectors.toSet());
		if (variant.getImages() == null) {
			variant.setImages(new LinkedHashSet<>());
		}
		Set<String> seen = new HashSet<>();
		boolean hasPrimary = images.stream()
				.anyMatch(ref -> Boolean.TRUE.equals(ref.primary()));
		int order = 0;
		for (var ref : images) {
			String url = ref.imageUrl() == null ? null : ref.imageUrl().trim();
			String key = url == null ? null : storageService.keyForUrl(url);
			if (url == null || url.isEmpty() || key == null || key.contains("..")
					|| !key.startsWith("variants/")) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"La imagen no pertenece al almacenamiento de variantes");
			}
			if (!storedKeys.contains(key)) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"La imagen seleccionada ya no existe");
			}
			if (!seen.add(key)) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Imagen duplicada en la variante");
			}
			VariantImage image = new VariantImage();
			image.setVariant(variant);
			image.setImageUrl(url);
			image.setIsPrimary(hasPrimary ? Boolean.TRUE.equals(ref.primary()) : order == 0);
			image.setSortOrder(++order);
			variant.getImages().add(image);
		}
	}

	private void validateVariants(List<ProductUpsertRequest.VariantRequest> variants) {
		Set<String> colors = new HashSet<>();
		for (var variant : variants) {
			String color = trimOrNull(variant.color());
			if (color == null || trimOrNull(variant.sku()) == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Cada variante debe tener color y SKU");
			}
			if (variant.price() == null || variant.price() <= 0) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Cada variante debe tener un precio mayor a cero");
			}
			if (variant.discountPercentage() != null
					&& (variant.discountPercentage() < 0 || variant.discountPercentage() > 100)) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"El descuento de cada variante debe estar entre 0 y 100");
			}
			if (!colors.add(color.toLowerCase(Locale.ROOT))) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"No puede haber variantes con el mismo color");
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

	private ProductImageDto toImageDto(VariantImage img) {
		return new ProductImageDto(img.getId(), img.getImageUrl(), img.getIsPrimary(), img.getSortOrder());
	}

	private AdminVariantDto toVariantDto(ProductVariant v) {
		List<ProductImageDto> images = v.getImages() == null ? List.of()
				: v.getImages().stream()
						.sorted(Comparator
								.comparing(VariantImage::getIsPrimary,
										Comparator.nullsLast(Comparator.reverseOrder()))
								.thenComparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE
										: img.getSortOrder()))
						.map(this::toImageDto)
						.toList();
		int discount = v.getDiscountPercentage() != null ? v.getDiscountPercentage() : 0;
		int discounted = v.getPrice() != null
				? v.getPrice() - (int) Math.round(v.getPrice() * discount / 100.0)
				: 0;
		return new AdminVariantDto(v.getId(), v.getColor(), v.getSku(), v.getPrice(),
				v.getDiscountPercentage(), discounted, v.getIsActive(), images);
	}

	private AdminProductDto toDto(Product p) {
		Brand brand = p.getBrand();
		List<AdminVariantDto> variants = p.getVariants() == null ? List.of()
				: p.getVariants().stream().sorted(Comparator
						.comparing(ProductVariant::getColor,
								Comparator.nullsLast(String::compareToIgnoreCase)))
						.map(this::toVariantDto).toList();
		List<String> categories = p.getCategories() == null ? List.of()
				: p.getCategories().stream().map(Category::getName).sorted().toList();

		return new AdminProductDto(p.getId(), p.getName(),
				brand != null ? brand.getId() : null, brand != null ? brand.getName() : null,
				p.getMaterial(), p.getShape(),
				p.getDescription(), p.getProductType(),
				p.getCreatedAt(), p.getUpdatedAt(), categories, variants);
	}
}
