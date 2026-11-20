package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.ProductItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sincroniza productos desde Softix. Cada SKU es un producto.
 *
 * Fuentes: products/list (qué SKUs existen, marca, referencia, color, precio)
 * y monturas/lista por sede (stock). Si cualquier llamada a Softix falla, no se
 * guarda nada: la base conserva los datos de la última sincronización correcta.
 */
@Service
@Slf4j
@AllArgsConstructor
public class SoftixCatalogSyncService {
    private static final String PRODUCT_TYPE = "MONTURA";
    private static final int PAGE_SIZE = 500;
    /** Sedes cuyo stock suma la disponibilidad de la web. B2 no entra. */
    private static final List<String> STOCK_BRANCHES = List.of("1", "2");

    private final SoftixClient softixClient;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    public record SyncResult(int created, int updated, int hidden, int skipped) {
    }

    @Transactional
    public SyncResult sync() {
        Map<String, ProductItem> softix = fetchCatalog();
        Map<String, Integer> stock = fetchStock();

        Map<String, Product> local = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            if (p.getSku() != null) {
                local.put(p.getSku(), p);
            }
        }
        Map<String, Brand> brands = new HashMap<>();
        brandRepository.findAll().forEach(b -> brands.put(b.getName(), b));
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));

        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        for (ProductItem item : softix.values()) {
            String sku = item.code().trim();
            String reference = trimOrNull(item.reference());
            String categoryName = normalize(item.category());
            Product product = local.get(sku);

            if (product == null) {
                if (reference == null || categoryName == null) {
                    log.warn("SKU {} omitido: falta referencia o categoría en Softix", sku);
                    skipped++;
                    continue;
                }
                product = new Product();
                product.setSku(sku);
                product.setStatus(ProductStatus.ACTIVE);
                local.put(sku, product);
                created++;
            } else {
                updated++;
                if (product.getStatus() == ProductStatus.HIDDEN) {
                    product.setStatus(ProductStatus.INACTIVE);
                }
            }

            String name = trimOrNull(item.name());
            if (name != null) {
                product.setName(name);
            }
            if (reference != null) {
                product.setReference(reference);
            }
            if (categoryName != null) {
                Category category = categories.computeIfAbsent(categoryName, n -> categoryRepository.save(newCategory(n)));
                product.setCategories(new LinkedHashSet<>(List.of(category)));
            }
            String brandName = normalize(item.brand());
            if (brandName != null) {
                product.setBrand(brands.computeIfAbsent(brandName, n -> brandRepository.save(newBrand(n))));
            }
            product.setColor(trimOrNull(item.color()));
            product.setPrice(item.salePrice() == null ? BigDecimal.ZERO : item.salePrice());
            product.setSaleVat(item.saleVat() != null && item.saleVat() == 1);
            product.setStock(Math.max(0, stock.getOrDefault(sku, 0)));
            product.setSoftixMissing(false);
            product.setSyncedAt(now);
            if (item.creationDate() != null) {
                product.setSoftixCreatedAt(item.creationDate());
            }
        }

        int hidden = 0;
        for (Product product : local.values()) {
            if (!softix.containsKey(product.getSku()) && product.getStatus() != ProductStatus.HIDDEN) {
                product.setStatus(ProductStatus.HIDDEN);
                product.setSoftixMissing(true);
                product.setSyncedAt(now);
                hidden++;
            }
        }

        productRepository.saveAll(local.values());
        log.info("Sincronización Softix: {} creados, {} actualizados, {} ocultos, {} omitidos",
                created, updated, hidden, skipped);
        return new SyncResult(created, updated, hidden, skipped);
    }

    private Map<String, ProductItem> fetchCatalog() {
        Map<String, ProductItem> items = new LinkedHashMap<>();
        int page = 1;
        int totalPages = 1;
        while (page <= totalPages) {
            var response = softixClient.listProducts(PRODUCT_TYPE, page, PAGE_SIZE);
            if (response.products() != null) {
                for (ProductItem item : response.products()) {
                    if (item.code() != null && !item.code().isBlank()) {
                        items.putIfAbsent(item.code().trim(), item);
                    }
                }
            }
            totalPages = response.totalPages() == null ? page : response.totalPages();
            page++;
        }
        return items;
    }

    private Map<String, Integer> fetchStock() {
        Map<String, Integer> stock = new HashMap<>();
        for (String branch : STOCK_BRANCHES) {
            for (var row : softixClient.listBranchStock(branch)) {
                if (row.codigo() != null) {
                    stock.merge(row.codigo().trim(), row.cantidad() == null ? 0 : row.cantidad(), Integer::sum);
                }
            }
        }
        return stock;
    }

    private static Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setIsFeatured(false);
        return category;
    }

    private static Brand newBrand(String name) {
        Brand brand = new Brand();
        brand.setName(name);
        brand.setIsFeatured(false);
        return brand;
    }

    /** Marca y categoría: sin espacios sobrantes y en mayúsculas, para no duplicarlas. */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
