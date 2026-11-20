package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AdminProductUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.PageResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductImageDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;

/**
 * AdminProductService — productos desde el panel de administración. Los datos de
 * Softix no se editan aquí; solo estado, material, forma, descripción e imágenes.
 */
@Service
@AllArgsConstructor
public class AdminProductService {

	private static final String IMAGE_FOLDER = "products";

	private final ProductRepository productRepository;
	private final StorageService storageService;

	@Transactional(readOnly = true)
	public PageResponse<AdminProductDto> list(String q, List<String> brands, List<String> categories,
			List<String> materials, List<String> shapes, List<String> colors,
			List<ProductStatus> statuses, BigDecimal priceMin, BigDecimal priceMax,
			String sort, int page, int size) {
		Specification<Product> spec = CatalogSpecifications.statusIn(statuses)
				.and(CatalogSpecifications.textSearch(q))
				.and(CatalogSpecifications.brandIn(brands))
				.and(CatalogSpecifications.categoryIn(categories))
				.and(CatalogSpecifications.materialIn(materials))
				.and(CatalogSpecifications.shapeIn(shapes))
				.and(CatalogSpecifications.colorIn(colors))
				.and(CatalogSpecifications.priceGte(priceMin))
				.and(CatalogSpecifications.priceLte(priceMax));

		Page<Product> result = productRepository.findAll(spec,
				PageRequest.of(page, size, CatalogSpecifications.sortFor(sort)));

		List<UUID> ids = result.getContent().stream().map(Product::getId).toList();
		Map<UUID, Product> details = ids.isEmpty() ? Map.of()
				: productRepository.findAllWithDetailsByIdIn(ids).stream()
						.collect(Collectors.toMap(Product::getId, Function.identity()));
		List<AdminProductDto> content = ids.stream().map(details::get).map(this::toDto).toList();

		return new PageResponse<>(content, result.getNumber(), result.getSize(),
				result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public AdminProductDto get(UUID id) {
		return toDto(load(id));
	}

	@Transactional
	public AdminProductDto update(UUID id, AdminProductUpdateRequest request) {
		Product product = load(id);
		if (request.status() != null) {
			product.setStatus(request.status());
		}
		if (request.material() != null) {
			product.setMaterial(blankToNull(request.material()));
		}
		if (request.shape() != null) {
			product.setShape(blankToNull(request.shape()));
		}
		if (request.description() != null) {
			product.setDescription(blankToNull(request.description()));
		}
		return toDto(product);
	}

	@Transactional
	public int bulkSetStatus(List<UUID> ids, ProductStatus status) {
		List<Product> products = productRepository.findAllById(ids);
		products.forEach(product -> product.setStatus(status));
		return products.size();
	}

	// ---------- imágenes del producto ----------

	@Transactional
	public ProductImageDto addImage(UUID productId, MultipartFile file, Boolean primary) {
		Product product = load(productId);
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
		}
		String url = storageService.store(file, IMAGE_FOLDER + "/" + productId);
		return addImageUrl(product, url, primary);
	}

	@Transactional
	public ProductImageDto addExistingImage(UUID productId, String url, Boolean primary) {
		Product product = load(productId);
		String key = storageService.keyForUrl(url);
		if (key == null || key.contains("..") || !key.startsWith(IMAGE_FOLDER + "/")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen no pertenece al almacenamiento de productos");
		}
		boolean exists = storageService.list(IMAGE_FOLDER).stream()
				.anyMatch(object -> object.key().equals(key));
		if (!exists) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "La imagen seleccionada ya no existe");
		}
		return addImageUrl(product, url, primary);
	}

	@Transactional
	public void deleteImage(UUID productId, UUID imageId) {
		Product product = load(productId);
		ProductImage image = findImage(product, imageId);
		boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
		product.getImages().remove(image);
		if (wasPrimary) {
			product.getImages().stream()
					.min(Comparator.comparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder()))
					.ifPresent(next -> next.setIsPrimary(true));
		}
	}

	@Transactional
	public ProductImageDto setPrimaryImage(UUID productId, UUID imageId) {
		Product product = load(productId);
		ProductImage target = findImage(product, imageId);
		product.getImages().forEach(img -> img.setIsPrimary(false));
		target.setIsPrimary(true);
		return toImageDto(target);
	}

	@Transactional
	public List<ProductImageDto> reorderImages(UUID productId, List<UUID> imageIds) {
		Product product = load(productId);
		List<ProductImage> images = product.getImages().stream().toList();
		if (images.isEmpty() && (imageIds == null || imageIds.isEmpty())) {
			return List.of();
		}
		if (imageIds == null || imageIds.size() != images.size()
				|| imageIds.size() != new LinkedHashSet<>(imageIds).size()
				|| images.stream().map(ProductImage::getId).anyMatch(id -> !imageIds.contains(id))) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El orden debe incluir exactamente todas las imágenes del producto");
		}

		Map<UUID, ProductImage> byId = images.stream()
				.collect(Collectors.toMap(ProductImage::getId, Function.identity()));
		for (int index = 0; index < imageIds.size(); index++) {
			byId.get(imageIds.get(index)).setSortOrder(index + 1);
		}
		ProductImage first = byId.get(imageIds.get(0));
		product.getImages().forEach(img -> img.setIsPrimary(img == first));
		return imageIds.stream().map(byId::get).map(this::toImageDto).toList();
	}

	// ---------- helpers ----------

	private ProductImageDto addImageUrl(Product product, String url, Boolean primary) {
		if (product.getImages() == null) {
			product.setImages(new LinkedHashSet<>());
		}
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

	private ProductImage findImage(Product product, UUID imageId) {
		return product.getImages().stream()
				.filter(img -> img.getId().equals(imageId))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
	}

	private Product load(UUID id) {
		return productRepository.findDetailById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
	}

	private static String blankToNull(String value) {
		return value.isBlank() ? null : value.trim();
	}

	private ProductImageDto toImageDto(ProductImage img) {
		return new ProductImageDto(img.getId(), img.getImageUrl(), img.getIsPrimary(), img.getSortOrder());
	}

	private AdminProductDto toDto(Product p) {
		List<ProductImageDto> images = p.getImages() == null ? List.of()
				: p.getImages().stream()
						.sorted(Comparator.comparing(ProductImage::getIsPrimary,
								Comparator.nullsLast(Comparator.reverseOrder()))
								.thenComparing(img -> img.getSortOrder() == null ? Integer.MAX_VALUE : img.getSortOrder()))
						.map(this::toImageDto)
						.toList();
		String imageUrl = images.isEmpty() ? null : images.get(0).imageUrl();
		List<String> categories = p.getCategories() == null ? List.of()
				: p.getCategories().stream().map(c -> c.getName()).sorted().toList();
		return new AdminProductDto(p.getId(), p.getSku(), p.getName(),
				p.getBrand() != null ? p.getBrand().getName() : null,
				p.getReference(), p.getColor(), categories, p.getPrice(), p.getSaleVat(),
				p.getStock(), p.getStatus(), p.getSoftixMissing(), p.getSyncedAt(),
				p.getMaterial(), p.getShape(), p.getDescription(), imageUrl, images);
	}
}
