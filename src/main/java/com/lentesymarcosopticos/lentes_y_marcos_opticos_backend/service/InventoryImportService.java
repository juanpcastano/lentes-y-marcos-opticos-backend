package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryConfirmResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryPreviewResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryPreviewRowDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductVariant;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductVariantRepository;

import lombok.AllArgsConstructor;

/**
 * InventoryImportService — aplica la importación previsualizada según las
 * decisiones del admin (rowKey → REPLACE | UPDATE | DISCARD).
 *
 * Sin stock: Cant es informativo, no se importa. Cada SKU crea/actualiza 1
 * producto con variante "Único". Si queda algún CONFLICTO sin decisión, no se
 * aplica nada (422 con el detalle por fila).
 */
@Service
@AllArgsConstructor
public class InventoryImportService {

	private final SoftixImportService softixImportService;
	private final ProductVariantRepository variantRepository;
	private final ProductRepository productRepository;
	private final BrandRepository brandRepository;
	private final CategoryRepository categoryRepository;

	@Transactional
	public InventoryConfirmResponse confirm(MultipartFile file, Map<String, String> decisions) {
		InventoryPreviewResponse preview = softixImportService.preview(file);
		Map<String, String> normalized = new LinkedHashMap<>();
		if (decisions != null) {
			decisions.forEach((key, value) -> {
				if (key != null && value != null) {
					normalized.put(key.trim(), value.trim().toUpperCase(Locale.ROOT));
				}
			});
		}

		Map<String, String> violations = new LinkedHashMap<>();
		for (InventoryPreviewRowDto row : preview.rows()) {
			if (!"CONFLICTO".equals(row.status())) {
				continue;
			}
			String decision = normalized.get(row.rowKey());
			if (!"REPLACE".equals(decision) && !"UPDATE".equals(decision)
					&& !"DISCARD".equals(decision)) {
				violations.put(row.rowKey(),
						"Conflicto sin resolver en SKU " + row.sku()
								+ ": elige Reemplazar, Actualizar o Descartar");
			}
		}
		if (!violations.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
					"Hay conflictos sin resolver: decide qué hacer con cada uno antes de importar",
					violations);
		}

		Map<String, Brand> brands = brandRepository.findAll().stream()
				.collect(Collectors.toMap(b -> norm(b.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
		Map<String, Category> categories = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(c -> norm(c.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));

		Map<String, Product> existingProducts = loadProducts(preview.rows());

		int created = 0, updated = 0, discarded = 0, unchanged = 0;
		List<String> skipped = new ArrayList<>();

		for (InventoryPreviewRowDto row : preview.rows()) {
			switch (row.status()) {
				case "ERROR" -> skipped.add("SKU " + row.sku() + ": " + row.detail());
				case "SIN_CAMBIOS" -> unchanged++;
				default -> {
					String decision = normalized.getOrDefault(row.rowKey(),
							"NUEVO".equals(row.status()) ? "CREATE" : "UPDATE");
					switch (decision) {
						case "DISCARD" -> discarded++;
						case "CREATE" -> {
							if (existingProducts.containsKey(row.sku())) {
								violations.put(row.rowKey(), "El SKU " + row.sku()
										+ " ya existe: usa Reemplazar o Actualizar");
							} else {
								createProduct(row, brands, categories);
								created++;
							}
						}
						case "UPDATE" -> {
							Product product = existingProducts.get(row.sku());
							if (product == null) {
								createProduct(row, brands, categories);
								created++;
							} else {
								product.setName(row.name().trim());
								product.setBasePrice(row.basePrice());
								updated++;
							}
						}
						case "REPLACE" -> {
							Product product = existingProducts.get(row.sku());
							if (product == null) {
								createProduct(row, brands, categories);
								created++;
							} else {
								product.setName(row.name().trim());
								product.setBasePrice(row.basePrice());
								product.setProductType(row.productType());
								product.setBrand(getOrCreateBrand(row.brand(), brands));
								product.setCategories(new HashSet<>(
										getOrCreateCategories(row.categories(), categories)));
								updated++;
							}
						}
						default -> violations.put(row.rowKey(), "Decisión no válida: " + decision);
					}
				}
			}
		}
		if (!violations.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
					"No se pudo aplicar la importación", violations);
		}
		return new InventoryConfirmResponse(created, updated, discarded, unchanged, skipped);
	}

	// ---------- helpers ----------

	private Map<String, Product> loadProducts(List<InventoryPreviewRowDto> rows) {
		List<String> skus = rows.stream()
				.filter(row -> !"ERROR".equals(row.status()) && !"SIN_CAMBIOS".equals(row.status()))
				.map(InventoryPreviewRowDto::sku).distinct().toList();
		if (skus.isEmpty()) {
			return Map.of();
		}
		List<ProductVariant> variants = variantRepository.findBySkuIn(skus);
		if (variants.isEmpty()) {
			return Map.of();
		}
		List<UUID> productIds = variants.stream().map(v -> v.getProduct().getId()).distinct()
				.toList();
		Map<UUID, Product> products = productRepository.findAllWithDetailsByIdIn(productIds)
				.stream().collect(Collectors.toMap(Product::getId, Function.identity()));
		Map<String, Product> bySku = new LinkedHashMap<>();
		for (ProductVariant variant : variants) {
			Product product = products.get(variant.getProduct().getId());
			if (product != null) {
				bySku.put(variant.getSku(), product);
			}
		}
		return bySku;
	}

	private void createProduct(InventoryPreviewRowDto row, Map<String, Brand> brands,
			Map<String, Category> categories) {
		Product product = new Product();
		product.setName(row.name().trim());
		product.setBasePrice(row.basePrice());
		product.setProductType(row.productType());
		product.setBrand(getOrCreateBrand(row.brand(), brands));
		product.setCategories(new HashSet<>(getOrCreateCategories(row.categories(), categories)));
		product.setIsActive(true);
		Product saved = productRepository.save(product);
		ProductVariant variant = new ProductVariant();
		variant.setProduct(saved);
		variant.setVariantName("Único");
		variant.setSku(row.sku());
		variant.setIsActive(true);
		saved.getVariants().add(variant);
		productRepository.saveAndFlush(saved);
	}

	private Brand getOrCreateBrand(String name, Map<String, Brand> cache) {
		String key = norm(name);
		Brand existing = cache.get(key);
		if (existing != null) {
			return existing;
		}
		Brand brand = new Brand();
		brand.setName(name.trim());
		brand.setIsFeatured(false);
		Brand saved = brandRepository.save(brand);
		cache.put(key, saved);
		return saved;
	}

	private List<Category> getOrCreateCategories(List<String> names, Map<String, Category> cache) {
		List<Category> result = new ArrayList<>();
		for (String name : names) {
			String key = norm(name);
			Category existing = cache.get(key);
			if (existing != null) {
				result.add(existing);
				continue;
			}
			Category category = new Category();
			category.setName(name.trim());
			category.setIsFeatured(false);
			Category saved = categoryRepository.save(category);
			cache.put(key, saved);
			result.add(saved);
		}
		return result;
	}

	private String norm(String value) {
		if (value == null) {
			return "";
		}
		return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
	}
}
