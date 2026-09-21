package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.ArrayList;
import java.util.HashSet;import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryConfirmResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryImportPlanRequest;
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
					if (row.basePrice() == null) {
						// Sin precio en el Excel: solo el wizard permite ponerlo
						// a mano (su validación lo exige). La ruta legacy lo omite.
						skipped.add("SKU " + row.sku()
								+ ": sin precio en el Excel (ponlo en el wizard de importación)");
					} else {
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
								variantPrice(product, row.sku()).ifPresent(v -> v.setPrice(row.basePrice()));
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
								variantPrice(product, row.sku()).ifPresent(v -> v.setPrice(row.basePrice()));
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
		}
		if (!violations.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
					"No se pudo aplicar la importación", violations);
		}
		return new InventoryConfirmResponse(created, updated, discarded, unchanged, skipped);
	}

	// ---------- helpers ----------

	/**
	 * Aplica el plan curado del wizard (marcas, categorías y productos ya
	 * decididos en el frontend). Valida todo antes de persistir: si hay
	 * violaciones responde 422 sin aplicar nada.
	 */
	@Transactional
	public InventoryConfirmResponse confirmPlan(InventoryImportPlanRequest plan) {
		if (plan == null || plan.products() == null || plan.products().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"El plan no contiene productos para importar");
		}
		Map<String, String> violations = new LinkedHashMap<>();

		// ---- marcas ----
		Map<String, Brand> brandsByRaw = new LinkedHashMap<>();
		if (plan.brands() != null) {
			for (var mapping : plan.brands()) {
				String raw = mapping == null ? null : trimOrNull(mapping.rawName());
				if (raw == null) {
					continue;
				}
				String key = SoftixImportService.normalize(raw);
				String action = mapping.action() == null ? ""
						: mapping.action().trim().toUpperCase(Locale.ROOT);
				switch (action) {
					case "OMIT" -> {
					}
					case "MAP" -> {
						if (mapping.brandId() == null) {
							violations.put("brand:" + raw, "Sin marca de destino para \"" + raw + "\"");
						} else {
							Brand brand = brandRepository.findById(mapping.brandId()).orElse(null);
							if (brand == null) {
								violations.put("brand:" + raw,
										"La marca de destino ya no existe (\"" + raw + "\")");
							} else {
								brandsByRaw.put(key, brand);
							}
						}
					}
					case "CREATE" -> {
						String finalName = trimOrNull(mapping.finalName());
						if (finalName == null) {
							violations.put("brand:" + raw, "Sin nombre final para \"" + raw + "\"");
						} else {
							brandsByRaw.put(key, getOrCreateBrand(finalName, brandCache()));
						}
					}
					default -> violations.put("brand:" + raw,
							"Acción no válida para la marca \"" + raw + "\": " + mapping.action());
				}
			}
		}

		// ---- categorías ----
		Map<String, Category> categoriesByRaw = new LinkedHashMap<>();
		if (plan.categories() != null) {
			for (var mapping : plan.categories()) {
				String raw = mapping == null ? null : trimOrNull(mapping.rawName());
				if (raw == null) {
					continue;
				}
				String key = SoftixImportService.normalize(raw);
				String action = mapping.action() == null ? ""
						: mapping.action().trim().toUpperCase(Locale.ROOT);
				switch (action) {
					case "OMIT" -> {
					}
					case "MAP" -> {
						if (mapping.categoryId() == null) {
							violations.put("category:" + raw,
									"Sin categoría de destino para \"" + raw + "\"");
						} else {
							Category category = categoryRepository.findById(mapping.categoryId())
									.orElse(null);
							if (category == null) {
								violations.put("category:" + raw,
										"La categoría de destino ya no existe (\"" + raw + "\")");
							} else {
								categoriesByRaw.put(key, category);
							}
						}
					}
					case "CREATE" -> {
						String finalName = trimOrNull(mapping.finalName());
						if (finalName == null) {
							violations.put("category:" + raw,
									"Sin nombre final para \"" + raw + "\"");
						} else {
							categoriesByRaw.put(key, getOrCreateCategory(finalName, categoryCache()));
						}
					}
					default -> violations.put("category:" + raw,
							"Acción no válida para la categoría \"" + raw + "\": " + mapping.action());
				}
			}
		}

		// ---- productos (validación, sin persistir) ----
		Set<String> seenSkus = new HashSet<>();
		int index = 0;
		for (var product : plan.products()) {
			index++;
			String ref = "products[" + index + "]";
			if (product == null) {
				violations.put(ref, "Producto vacío");
				continue;
			}
			if (trimOrNull(product.name()) == null) {
				violations.put(ref, "Producto sin nombre");
			}
			Brand brand = product.brandRaw() == null ? null
					: brandsByRaw.get(SoftixImportService.normalize(product.brandRaw()));
			if (brand == null) {
				violations.put(ref,
						"La marca \"" + product.brandRaw() + "\" está omitida o sin mapear");
			}
			if (product.variants() == null || product.variants().stream()
					.noneMatch(v -> v != null && !"DISCARD".equals(normMode(v.mode())))) {
				violations.put(ref, "El producto \"" + product.name() + "\" no tiene variantes a importar");
			}
			if (product.existingProductId() != null
					&& !productRepository.existsById(product.existingProductId())) {
				violations.put(ref, "El producto existente enlazado ya no existe");
			}
			if (product.variants() != null) {
				Set<String> colors = new HashSet<>();
				for (var variant : product.variants()) {
					if (variant == null) {
						continue;
					}
					String mode = normMode(variant.mode());
					if ("DISCARD".equals(mode)) {
						continue;
					}
					if (!Set.of("CREATE", "UPDATE", "REPLACE").contains(mode)) {
						violations.put("sku:" + variant.sku(), "Modo no válido: " + variant.mode());
						continue;
					}
					String sku = trimOrNull(variant.sku());
					if (sku == null) {
						violations.put(ref, "Variante sin SKU en \"" + product.name() + "\"");
						continue;
					}
					if (!seenSkus.add(sku)) {
						violations.put("sku:" + sku, "SKU duplicado en el plan");
						continue;
					}
					String color = trimOrNull(variant.color());
					if (color == null) {
						violations.put("sku:" + sku, "Variante sin color");
					} else if (!colors.add(color.toLowerCase(Locale.ROOT))) {
						violations.put("sku:" + sku,
								"Color duplicado en el producto \"" + product.name() + "\": " + color);
					}
					if (variant.price() == null || variant.price() <= 0) {
						violations.put("sku:" + sku, "Precio no válido");
					}
				}
			}
		}
		if (!violations.isEmpty()) {
			throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
					"El plan tiene errores: revísalos antes de importar", violations);
		}

		// ---- aplicar ----
		Map<String, ProductVariant> variantsBySku = variantRepository
				.findBySkuIn(seenSkus.stream().toList()).stream()
				.collect(Collectors.toMap(ProductVariant::getSku, Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
		Map<UUID, Product> productsById = new LinkedHashMap<>();

		int created = 0, updated = 0, discarded = 0, unchanged = 0;
		Set<UUID> touchedProducts = new HashSet<>();
		for (var planned : plan.products()) {
			Brand brand = brandsByRaw
					.get(SoftixImportService.normalize(planned.brandRaw()));
			List<Category> categories = planned.categoryRaws() == null ? List.of()
					: planned.categoryRaws().stream()
							.map(raw -> categoriesByRaw.get(SoftixImportService.normalize(raw)))
							.filter(Objects::nonNull)
							.distinct()
							.toList();

			Product target;
			boolean productChanged;
			if (planned.existingProductId() != null) {
				target = productsById.computeIfAbsent(planned.existingProductId(),
						id -> productRepository.findDetailById(id).orElseThrow());
				String newName = planned.name().trim();
				// Las categorías se suman a las que ya tiene el producto: el
				// Excel agrega, nunca quita.
				Set<Category> merged = new LinkedHashSet<>(
						target.getCategories() == null ? Set.of() : target.getCategories());
				merged.addAll(categories);
				productChanged = !Objects.equals(target.getName(), newName)
						|| target.getBrand() == null
						|| !target.getBrand().getId().equals(brand.getId())
						|| !Objects.equals(target.getProductType(), planned.productType())
						|| !categoryIds(target.getCategories()).equals(categoryIds(merged));
				target.setName(newName);
				target.setProductType(planned.productType());
				target.setBrand(brand);
				target.setCategories(new HashSet<>(merged));
			} else {
				target = new Product();
				target.setName(planned.name().trim());
				target.setProductType(planned.productType());
				target.setBrand(brand);
				target.setCategories(new HashSet<>(categories));
				target = productRepository.save(target);
				productsById.put(target.getId(), target);
				productChanged = true;
			}

			for (var plannedVariant : planned.variants()) {
				String mode = normMode(plannedVariant.mode());
				if ("DISCARD".equals(mode)) {
					discarded++;
					continue;
				}
				String sku = plannedVariant.sku().trim();
				String color = plannedVariant.color().trim();
				ProductVariant variant = variantsBySku.get(sku);
				if (variant == null) {
					variant = new ProductVariant();
					variant.setProduct(target);
					variant.setSku(sku);
					variant.setColor(color);
					variant.setPrice(plannedVariant.price());
					variant.setIsActive(true);
					target.getVariants().add(variant);
					variantsBySku.put(sku, variant);
					created++;
				} else {
					boolean sameColor = variant.getColor() != null
							&& variant.getColor().equalsIgnoreCase(color);
					boolean samePrice = Objects.equals(variant.getPrice(), plannedVariant.price());
					boolean sameProduct = variant.getProduct() != null
							&& variant.getProduct().getId().equals(target.getId());
					variant.setColor(color);
					variant.setPrice(plannedVariant.price());
					if (!sameProduct) {
						if (variant.getProduct() != null) {
							touchedProducts.add(variant.getProduct().getId());
						}
						variant.setProduct(target);
						target.getVariants().add(variant);
					}
					if (!productChanged && sameColor && samePrice && sameProduct) {
						unchanged++;
					} else {
						updated++;
					}
				}
			}
		}
		productRepository.flush();
		// Limpieza: los productos que perdieron variantes por fusiones y
		// quedaron sin ninguna se eliminan (un producto sin variantes no es
		// vendible ni visible). Solo toca productos afectados por este plan.
		for (UUID productId : touchedProducts) {
			if (productsById.containsKey(productId)) {
				continue;
			}
			if (variantRepository.countByProductId(productId) == 0) {
				productRepository.findById(productId).ifPresent(productRepository::delete);
			}
		}
		return new InventoryConfirmResponse(created, updated, discarded, unchanged, List.of());
	}

	private String normMode(String mode) {
		return mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
	}

	private Set<UUID> categoryIds(java.util.Collection<Category> categories) {
		if (categories == null) {
			return Set.of();
		}
		return categories.stream().map(Category::getId).collect(Collectors.toSet());
	}

	private String trimOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private Map<String, Brand> brandCache() {
		return brandRepository.findAll().stream()
				.collect(Collectors.toMap(b -> norm(b.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
	}

	private Map<String, Category> categoryCache() {
		return categoryRepository.findAll().stream()
				.collect(Collectors.toMap(c -> norm(c.getName()), Function.identity(),
						(a, b) -> a, LinkedHashMap::new));
	}

	private Category getOrCreateCategory(String name, Map<String, Category> cache) {
		String key = norm(name);
		Category existing = cache.get(key);
		if (existing != null) {
			return existing;
		}
		Category category = new Category();
		category.setName(name.trim());
		category.setIsFeatured(false);
		Category saved = categoryRepository.save(category);
		cache.put(key, saved);
		return saved;
	}

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
		product.setProductType(row.productType());
		product.setBrand(getOrCreateBrand(row.brand(), brands));
		product.setCategories(new HashSet<>(getOrCreateCategories(row.categories(), categories)));
		Product saved = productRepository.save(product);
		ProductVariant variant = new ProductVariant();
		variant.setProduct(saved);
		variant.setColor("ÚNICO");
		variant.setSku(row.sku());
		variant.setPrice(row.basePrice());
		variant.setIsActive(true);
		saved.getVariants().add(variant);
		productRepository.saveAndFlush(saved);
	}

	private java.util.Optional<ProductVariant> variantPrice(Product product, String sku) {
		if (product.getVariants() == null) {
			return java.util.Optional.empty();
		}
		return product.getVariants().stream()
				.filter(v -> sku.equals(v.getSku()))
				.findFirst();
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
