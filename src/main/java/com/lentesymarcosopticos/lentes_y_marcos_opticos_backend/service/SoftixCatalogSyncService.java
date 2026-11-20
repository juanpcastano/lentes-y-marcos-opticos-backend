package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

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
 * Sincroniza los productos guardados con Softix. Solo actualiza y oculta: nunca crea productos.
 * La creación pertenece al flujo de importación.
 *
 * Fuentes: products/list (existencia, marca, referencia, color, categoría, nombre y precio)
 * y monturas/lista por sede (stock). Si una llamada a Softix falla, no se aplica nada.
 *
 * Un SKU con error bloqueante (sin categoría, sin referencia, referencia repetida en marcas
 * distintas, o duplicado de marca, referencia y color) conserva sus datos anteriores y el error
 * queda registrado. Los demás SKUs se procesan normalmente.
 */
@Service
@Slf4j
@AllArgsConstructor
public class SoftixCatalogSyncService {
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    private static final String PRODUCT_TYPE = "MONTURA";
    private static final int PAGE_SIZE = 500;
    /** Sedes cuyo stock suma la disponibilidad de la web. B2 no entra. */
    private static final List<String> STOCK_BRANCHES = List.of("1", "2");
    private static final String HIDDEN_NAME = ProductStatus.HIDDEN.name();

    private final SoftixClient softixClient;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    /** Un cambio (INFO), un aviso (WARN) o un error (ERROR) de un SKU. */
    public record Entry(String level, String sku, String field, String oldValue, String newValue, String message) {
    }

    public record SyncResult(int updated, int hidden, int errors,
            int stockChanged, int priceChanged, List<Entry> entries) {
    }

    @Transactional
    public SyncResult sync() {
        return process(true);
    }

    /** Calcula lo que cambiaría una sincronización sin escribir nada. */
    @Transactional(readOnly = true)
    public SyncResult preview() {
        return process(false);
    }

    private SyncResult process(boolean apply) {
        Map<String, ProductItem> softix = fetchCatalog();
        Map<String, Integer> stock = fetchStock();
        Map<String, String> blocking = blockingErrors(softix);
        Map<String, Product> local = localBySku();
        Map<String, Brand> brands = new HashMap<>();
        brandRepository.findAll().forEach(b -> brands.put(b.getName(), b));
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));

        LocalDateTime now = LocalDateTime.now();
        List<Entry> entries = new ArrayList<>();
        List<Product> changedProducts = new ArrayList<>();
        int updated = 0;
        int errors = 0;
        int stockChanged = 0;
        int priceChanged = 0;

        for (ProductItem item : sortedBySku(softix)) {
            String sku = item.code().trim();
            Product product = local.get(sku);
            if (product == null) {
                // SKU nuevo en Softix: no es responsabilidad de la sincronización.
                // Se importa con el flujo de importación.
                continue;
            }
            String error = blocking.get(sku);

            if (error != null) {
                errors++;
                entries.add(new Entry(LEVEL_ERROR, sku, null, null, null,
                        error + ". Se conservan los datos anteriores."));
                continue;
            }
            List<Entry> changes = new ArrayList<>();
            String name = trimOrNull(item.name());
            String reference = trimOrNull(item.reference());
            String color = trimOrNull(item.color());
            String brandName = normalize(item.brand());
            String categoryName = normalize(item.category());
            BigDecimal newPrice = item.salePrice() == null ? BigDecimal.ZERO : item.salePrice();
            int newStock = Math.max(0, stock.getOrDefault(sku, 0));
            String oldBrand = product.getBrand() == null ? null : product.getBrand().getName();
            String oldCategory = product.getCategories().isEmpty()
                    ? null
                    : product.getCategories().iterator().next().getName();

            if (name != null) {
                diff(changes, sku, "nombre", product.getName(), name);
            }
            diff(changes, sku, "referencia", product.getReference(), reference);
            diff(changes, sku, "color", product.getColor(), color);
            if (brandName != null) {
                diff(changes, sku, "marca", oldBrand, brandName);
                normalizationWarning(changes, sku, "marca", item.brand(), brandName, oldBrand);
                if (!brandName.equals(oldBrand) && !brands.containsKey(brandName)) {
                    changes.add(new Entry(LEVEL_WARN, sku, "marca", null, brandName, "Marca nueva"));
                }
            }
            if (categoryName != null) {
                diff(changes, sku, "categoría", oldCategory, categoryName);
                normalizationWarning(changes, sku, "categoría", item.category(), categoryName, oldCategory);
                if (!categoryName.equals(oldCategory) && !categories.containsKey(categoryName)) {
                    changes.add(new Entry(LEVEL_WARN, sku, "categoría", null, categoryName, "Categoría nueva"));
                }
            }
            if (product.getPrice() == null || product.getPrice().compareTo(newPrice) != 0) {
                changes.add(new Entry(LEVEL_INFO, sku, "precio", valueOf(product.getPrice()),
                        newPrice.toPlainString(), "Cambio de precio"));
                priceChanged++;
            }
            if (!Objects.equals(product.getStock(), newStock)) {
                changes.add(new Entry(LEVEL_INFO, sku, "stock", valueOf(product.getStock()),
                        String.valueOf(newStock), "Cambio de stock"));
                stockChanged++;
            }
            if (product.getStatus() == ProductStatus.HIDDEN) {
                changes.add(new Entry(LEVEL_INFO, sku, "estado", HIDDEN_NAME, ProductStatus.INACTIVE.name(),
                        "Vuelve a aparecer en Softix"));
            }

            if (!changes.isEmpty()) {
                updated++;
                entries.addAll(changes);
            }
            if (apply) {
                if (name != null) {
                    product.setName(name);
                }
                product.setReference(reference);
                if (color != null) {
                    product.setColor(color);
                }
                if (brandName != null) {
                    product.setBrand(brands.computeIfAbsent(brandName, n -> brandRepository.save(newBrand(n))));
                }
                if (categoryName != null) {
                    Category category = categories.computeIfAbsent(categoryName,
                            n -> categoryRepository.save(newCategory(n)));
                    product.setCategories(new LinkedHashSet<>(List.of(category)));
                }
                product.setPrice(newPrice);
                product.setStock(newStock);
                product.setSaleVat(item.saleVat() != null && item.saleVat() == 1);
                if (product.getStatus() == ProductStatus.HIDDEN) {
                    product.setStatus(ProductStatus.INACTIVE);
                }
                product.setSoftixMissing(false);
                product.setSyncedAt(now);
                if (item.creationDate() != null) {
                    product.setSoftixCreatedAt(item.creationDate());
                }
                changedProducts.add(product);
            }
        }

        int hidden = 0;
        for (Product product : local.values()) {
            if (softix.containsKey(product.getSku()) || product.getStatus() == ProductStatus.HIDDEN) {
                continue;
            }
            hidden++;
            entries.add(new Entry(LEVEL_INFO, product.getSku(), "estado", valueOf(product.getStatus()),
                    HIDDEN_NAME, "Oculto: ya no aparece en Softix"));
            if (apply) {
                product.setStatus(ProductStatus.HIDDEN);
                product.setSoftixMissing(true);
                product.setSyncedAt(now);
                changedProducts.add(product);
            }
        }

        if (apply && !changedProducts.isEmpty()) {
            productRepository.saveAll(changedProducts);
        }
        entries.sort(Comparator.comparingInt(entry -> levelRank(entry.level())));
        log.info("Sincronización Softix ({}): {} actualizados, {} ocultos, {} con error",
                apply ? "aplicada" : "vista previa", updated, hidden, errors);
        return new SyncResult(updated, hidden, errors, stockChanged, priceChanged, entries);
    }

    /** Errores que bloquean un SKU, por su código. Un SKU aparece una sola vez con su primer motivo. */
    private Map<String, String> blockingErrors(Map<String, ProductItem> softix) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Set<String>> brandsByReference = new HashMap<>();
        Map<String, List<String>> skusByIdentity = new HashMap<>();

        for (ProductItem item : softix.values()) {
            String sku = item.code().trim();
            if (normalize(item.category()) == null) {
                errors.put(sku, "Sin categoría en Softix");
                continue;
            }
            String reference = trimOrNull(item.reference());
            if (reference == null) {
                errors.put(sku, "Sin referencia en Softix");
                continue;
            }
            String referenceKey = reference.toUpperCase(Locale.ROOT);
            String brandKey = Objects.toString(normalize(item.brand()), "");
            String colorKey = Objects.toString(trimOrNull(item.color()), "").toUpperCase(Locale.ROOT);
            brandsByReference.computeIfAbsent(referenceKey, k -> new TreeSet<>()).add(brandKey);
            skusByIdentity.computeIfAbsent(brandKey + "|" + referenceKey + "|" + colorKey, k -> new ArrayList<>())
                    .add(sku);
        }

        for (ProductItem item : softix.values()) {
            String sku = item.code().trim();
            String reference = trimOrNull(item.reference());
            if (errors.containsKey(sku) || reference == null) {
                continue;
            }
            Set<String> brands = brandsByReference.get(reference.toUpperCase(Locale.ROOT));
            if (brands != null && brands.size() > 1) {
                errors.put(sku, "Referencia " + reference + " repetida en marcas distintas: "
                        + String.join(", ", brands));
            }
        }

        for (List<String> skus : skusByIdentity.values()) {
            if (skus.size() < 2) {
                continue;
            }
            for (String sku : skus) {
                if (errors.containsKey(sku)) {
                    continue;
                }
                List<String> others = skus.stream().filter(other -> !other.equals(sku)).toList();
                errors.put(sku, "Duplicado de marca, referencia y color con SKU " + String.join(", ", others));
            }
        }
        return errors;
    }

    private void diff(List<Entry> changes, String sku, String field, String oldValue, String newValue) {
        if (newValue != null && !Objects.equals(oldValue, newValue)) {
            changes.add(new Entry(LEVEL_INFO, sku, field, oldValue, newValue, "Cambio de " + field));
        }
    }

    private void normalizationWarning(List<Entry> changes, String sku, String field, String raw,
            String normalized, String oldValue) {
        String trimmed = trimOrNull(raw);
        if (trimmed != null && !trimmed.equals(normalized) && !normalized.equals(oldValue)) {
            changes.add(new Entry(LEVEL_WARN, sku, field, trimmed, normalized,
                    "Se guarda normalizado; corregir en Softix"));
        }
    }

    private static int levelRank(String level) {
        return switch (level) {
            case LEVEL_ERROR -> 0;
            case LEVEL_WARN -> 1;
            default -> 2;
        };
    }

    private static List<ProductItem> sortedBySku(Map<String, ProductItem> softix) {
        return softix.values().stream()
                .sorted(Comparator.comparing(item -> item.code().trim()))
                .toList();
    }

    private static String valueOf(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Product> localBySku() {
        Map<String, Product> local = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            if (p.getSku() != null) {
                local.put(p.getSku(), p);
            }
        }
        return local;
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
