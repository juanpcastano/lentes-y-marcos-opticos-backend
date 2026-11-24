package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventAction;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventEntity;
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
 * Un SKU con error bloqueante (sin categoría, sin marca, sin referencia, sin precio válido,
 * referencia repetida en marcas distintas, o duplicado de marca, referencia y color) conserva
 * sus datos anteriores y el error queda registrado. Los demás SKUs se procesan normalmente.
 * La identidad de marca, categoría, referencia y color es exacta: solo se recortan y colapsan
 * espacios; un cambio de mayúsculas es otro valor.
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

    private final SoftixClient softixClient;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Un evento de la ejecución: cambio (INFO), aviso (WARN) o error (ERROR). La clave es el SKU
     * para productos y el nombre para marcas y categorías.
     */
    public record Entry(String level, SyncEventEntity entityType, SyncEventAction action, String key,
            String field, String oldValue, String newValue, String message) {
    }

    public record SyncResult(int updated, int hidden, int errors,
            int stockChanged, int priceChanged, List<Entry> entries) {
    }

    /** Estado de un SKU nuevo respecto de la importación. */
    public enum NewSkuState {
        VALID, BLOCKED, IGNORED_BY_BRAND, IGNORED_BY_SKU
    }

    /** Un SKU nuevo de Softix con su estado de importación. */
    public record NewSku(String sku, String brand, String reference, String color, String category,
            String name, BigDecimal price, int stock, NewSkuState state, String blockReason) {
    }

    /** Clasificación del catálogo para la importación, sin escribir nada. */
    public record ImportPreview(List<NewSku> newSkus, List<String> brandsToCreate,
            List<String> categoriesToCreate) {
    }

    /** Resultado de una importación real. */
    public record ImportResult(List<String> createdSkus, List<String> createdBrands,
            List<String> createdCategories, List<NewSku> newSkus) {

        public List<NewSku> blocked() {
            return newSkus.stream().filter(s -> s.state() == NewSkuState.BLOCKED).toList();
        }
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

    /**
     * Crea los productos nuevos de Softix que están en estado VALID. Omite marcas ignoradas,
     * SKUs ignorados y SKUs bloqueados. Devuelve lo que se creó realmente, incluidas las
     * marcas y categorías nuevas. No ejecuta la sincronización posterior.
     */
    @Transactional
    public ImportResult importNewProducts(Set<String> ignoredBrands, Set<String> ignoredSkus) {
        Map<String, ProductItem> softix = fetchCatalog();
        Map<String, Integer> stock = fetchStock();
        requireNonEmptySources(softix, stock);
        Map<String, String> blocking = blockingErrors(softix);

        Map<String, Brand> brands = new HashMap<>();
        brandRepository.findAll().forEach(b -> brands.put(b.getName(), b));
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));

        ImportPreview preview = classify(softix, stock, blocking, localSkus(), ignoredBrands, ignoredSkus,
                brands.keySet(), categories.keySet());

        List<String> createdSkus = new ArrayList<>();
        List<String> createdBrands = new ArrayList<>();
        List<String> createdCategories = new ArrayList<>();
        List<Product> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (NewSku ns : preview.newSkus()) {
            if (ns.state() != NewSkuState.VALID) {
                continue;
            }
            Brand brand = brands.get(ns.brand());
            if (brand == null) {
                brand = brandRepository.save(newBrand(ns.brand()));
                brands.put(ns.brand(), brand);
                createdBrands.add(ns.brand());
            }
            Category category = categories.get(ns.category());
            if (category == null) {
                category = categoryRepository.save(newCategory(ns.category()));
                categories.put(ns.category(), category);
                createdCategories.add(ns.category());
            }

            Product product = new Product();
            product.setSku(ns.sku());
            product.setName(ns.name());
            product.setReference(ns.reference());
            product.setColor(ns.color());
            product.setBrand(brand);
            product.setPrice(ns.price());
            product.setStock(ns.stock());
            ProductItem item = softix.get(ns.sku());
            product.setSaleVat(item != null && item.saleVat() != null && item.saleVat() == 1);
            product.setStatus(ProductStatus.ACTIVE);
            product.setSoftixMissing(false);
            product.setSyncedAt(now);
            if (item != null && item.creationDate() != null) {
                product.setSoftixCreatedAt(item.creationDate());
            }
            product.setCategories(new LinkedHashSet<>(List.of(category)));
            toSave.add(product);
            createdSkus.add(ns.sku());
        }
        if (!toSave.isEmpty()) {
            productRepository.saveAll(toSave);
        }
        return new ImportResult(createdSkus, createdBrands, createdCategories, preview.newSkus());
    }

    /** Clasifica el catálogo nuevo sin crear nada. Usa el mismo cálculo que la importación real. */
    @Transactional(readOnly = true)
    public ImportPreview previewImport(Set<String> ignoredBrands, Set<String> ignoredSkus) {
        Map<String, ProductItem> softix = fetchCatalog();
        Map<String, Integer> stock = fetchStock();
        Map<String, String> blocking = blockingErrors(softix);
        Set<String> localBrands = new HashSet<>();
        brandRepository.findAll().forEach(b -> localBrands.add(b.getName()));
        Set<String> localCategories = new HashSet<>();
        categoryRepository.findAll().forEach(c -> localCategories.add(c.getName()));
        return classify(softix, stock, blocking, localSkus(), ignoredBrands, ignoredSkus,
                localBrands, localCategories);
    }

    private ImportPreview classify(Map<String, ProductItem> softix, Map<String, Integer> stock,
            Map<String, String> blocking, Set<String> localSkus, Set<String> ignoredBrands,
            Set<String> ignoredSkus, Set<String> localBrands, Set<String> localCategories) {
        Set<String> ignoredBrandSet = ignoredBrands == null ? Set.of() : ignoredBrands;
        Set<String> ignoredSkuSet = ignoredSkus == null ? Set.of() : ignoredSkus;
        Set<String> local = localSkus == null ? Set.of() : localSkus;
        List<NewSku> newSkus = new ArrayList<>();
        Set<String> brandsToCreate = new TreeSet<>();
        Set<String> categoriesToCreate = new TreeSet<>();

        for (ProductItem item : sortedBySku(softix)) {
            String sku = item.code().trim();
            if (local.contains(sku)) {
                continue;
            }
            String brand = normalize(item.brand());
            String category = normalize(item.category());
            String reference = normalize(item.reference());
            String color = normalize(item.color());
            String name = trimOrNull(item.name());
            if (name == null) {
                name = sku;
            }
            BigDecimal price = item.salePrice() == null ? BigDecimal.ZERO : item.salePrice();
            int available = Math.max(0, stock.getOrDefault(sku, 0));

            NewSkuState state;
            String reason = null;
            if (brand != null && ignoredBrandSet.contains(brand)) {
                state = NewSkuState.IGNORED_BY_BRAND;
            } else if (ignoredSkuSet.contains(sku)) {
                state = NewSkuState.IGNORED_BY_SKU;
            } else if (blocking.containsKey(sku)) {
                state = NewSkuState.BLOCKED;
                reason = blocking.get(sku);
            } else {
                state = NewSkuState.VALID;
            }

            newSkus.add(new NewSku(sku, brand, reference, color, category, name, price, available,
                    state, reason));
            if (state == NewSkuState.VALID) {
                if (brand != null && !localBrands.contains(brand)) {
                    brandsToCreate.add(brand);
                }
                if (category != null && !localCategories.contains(category)) {
                    categoriesToCreate.add(category);
                }
            }
        }
        return new ImportPreview(newSkus, new ArrayList<>(brandsToCreate),
                new ArrayList<>(categoriesToCreate));
    }

    private SyncResult process(boolean apply) {
        Map<String, ProductItem> softix = fetchCatalog();
        Map<String, Integer> stock = fetchStock();
        requireNonEmptySources(softix, stock);
        Map<String, String> blocking = blockingErrors(softix);
        Map<String, Product> local = localBySku();
        Map<String, Brand> brands = new HashMap<>();
        brandRepository.findAll().forEach(b -> brands.put(b.getName(), b));
        Map<String, Category> categories = new HashMap<>();
        categoryRepository.findAll().forEach(c -> categories.put(c.getName(), c));
        java.util.Set<String> knownBrands = new java.util.HashSet<>(brands.keySet());
        java.util.Set<String> knownCategories = new java.util.HashSet<>(categories.keySet());

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
                entries.add(new Entry(LEVEL_ERROR, SyncEventEntity.PRODUCT, SyncEventAction.BLOCKED, sku, null, null, null,
                        error + ". Se conservan los datos anteriores."));
                continue;
            }
            List<Entry> changes = new ArrayList<>();
            String name = trimOrNull(item.name());
            String reference = normalize(item.reference());
            String color = normalize(item.color());
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
                if (!brandName.equals(oldBrand) && knownBrands.add(brandName)) {
                    changes.add(new Entry(LEVEL_INFO, SyncEventEntity.BRAND, SyncEventAction.CREATED, brandName, null, null, null, "Marca nueva"));
                }
            }
            if (categoryName != null) {
                diff(changes, sku, "categoría", oldCategory, categoryName);
                normalizationWarning(changes, sku, "categoría", item.category(), categoryName, oldCategory);
                if (!categoryName.equals(oldCategory) && knownCategories.add(categoryName)) {
                    changes.add(new Entry(LEVEL_INFO, SyncEventEntity.CATEGORY, SyncEventAction.CREATED, categoryName, null, null, null, "Categoría nueva"));
                }
            }
            if (product.getPrice() == null || product.getPrice().compareTo(newPrice) != 0) {
                changes.add(new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.UPDATED, sku, "precio", valueOf(product.getPrice()),
                        newPrice.toPlainString(), "Cambio de precio"));
                priceChanged++;
            }
            if (!Objects.equals(product.getStock(), newStock)) {
                changes.add(new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.UPDATED, sku, "stock", valueOf(product.getStock()),
                        String.valueOf(newStock), "Cambio de stock"));
                stockChanged++;
            }
            if (Boolean.TRUE.equals(product.getSoftixMissing())) {
                changes.add(new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.RESTORED, sku, "softixMissing", "true", "false",
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
                    Brand b = brands.get(brandName);
                    if (b == null) {
                        b = brandRepository.save(newBrand(brandName));
                        brands.put(brandName, b);
                    }
                    product.setBrand(b);
                }
                if (categoryName != null) {
                    Category category = categories.get(categoryName);
                    if (category == null) {
                        category = categoryRepository.save(newCategory(categoryName));
                        categories.put(categoryName, category);
                    }
                    product.setCategories(new LinkedHashSet<>(List.of(category)));
                }
                product.setPrice(newPrice);
                product.setStock(newStock);
                product.setSaleVat(item.saleVat() != null && item.saleVat() == 1);
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
            if (softix.containsKey(product.getSku()) || Boolean.TRUE.equals(product.getSoftixMissing())) {
                continue;
            }
            hidden++;
            entries.add(new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.HIDDEN, product.getSku(), "softixMissing", "false",
                    "true", "Oculto: ya no aparece en Softix"));
            if (apply) {
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

    private static void requireNonEmptySources(Map<String, ProductItem> catalog, Map<String, Integer> stock) {
        if (catalog.isEmpty()) {
            throw new IllegalStateException("Softix devolvió un catálogo vacío");
        }
        if (stock.isEmpty()) {
            throw new IllegalStateException("Softix devolvió un stock vacío");
        }
    }

    /** Errores que bloquean un SKU, por su código. Un SKU aparece una sola vez con su primer motivo. */
    public Map<String, String> blockingErrors(Map<String, ProductItem> softix) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, Set<String>> brandsByReference = new HashMap<>();
        Map<String, List<String>> skusByIdentity = new HashMap<>();

        for (ProductItem item : softix.values()) {
            String sku = item.code().trim();
            String category = normalize(item.category());
            String reference = normalize(item.reference());
            String brand = normalize(item.brand());
            String color = normalize(item.color());

            if (category == null) {
                errors.put(sku, "Sin categoría en Softix");
            } else if (reference == null) {
                errors.put(sku, "Sin referencia en Softix");
            } else if (brand == null) {
                errors.put(sku, "Sin marca en Softix");
            } else if (item.salePrice() == null || item.salePrice().signum() <= 0) {
                errors.put(sku, "Sin precio válido en Softix");
            }

            // Las reglas de duplicados miran la identidad exacta aunque el SKU ya esté bloqueado
            // por otro motivo: la referencia sigue existiendo en Softix.
            if (reference != null && brand != null) {
                brandsByReference.computeIfAbsent(reference, k -> new TreeSet<>()).add(brand);
                String identity = brand + "|" + reference + "|" + Objects.toString(color, "");
                skusByIdentity.computeIfAbsent(identity, k -> new ArrayList<>()).add(sku);
            }
        }

        for (ProductItem item : softix.values()) {
            String sku = item.code().trim();
            String reference = normalize(item.reference());
            if (errors.containsKey(sku) || reference == null) {
                continue;
            }
            Set<String> brands = brandsByReference.get(reference);
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
            changes.add(new Entry(LEVEL_INFO, SyncEventEntity.PRODUCT, SyncEventAction.UPDATED, sku, field, oldValue, newValue, "Cambio de " + field));
        }
    }

    private void normalizationWarning(List<Entry> changes, String sku, String field, String raw,
            String normalized, String oldValue) {
        String trimmed = trimOrNull(raw);
        if (trimmed != null && !trimmed.equals(normalized) && !normalized.equals(oldValue)) {
            changes.add(new Entry(LEVEL_WARN, SyncEventEntity.PRODUCT, SyncEventAction.UPDATED, sku, field, trimmed, normalized,
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

    public Set<String> localSkus() {
        Set<String> skus = new HashSet<>();
        for (Product p : productRepository.findAll()) {
            if (p.getSku() != null) {
                skus.add(p.getSku());
            }
        }
        return skus;
    }

    public Map<String, ProductItem> fetchCatalog() {
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

    public Map<String, Integer> fetchStock() {
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

    /**
     * Marca, categoría, referencia y color: sin espacios sobrantes y con la capitalización
     * original. La comparación es exacta, así que un cambio de mayúsculas crea otro valor.
     */
    public static String normalizePublic(String value) {
        return normalize(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
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
