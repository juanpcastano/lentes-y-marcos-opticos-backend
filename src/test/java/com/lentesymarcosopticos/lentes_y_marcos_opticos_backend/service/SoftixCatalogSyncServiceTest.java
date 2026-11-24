package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SyncEventAction;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.ProductItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.ProductPage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.StockItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixCatalogSyncService.SyncResult;

@ExtendWith(MockitoExtension.class)
class SoftixCatalogSyncServiceTest {
    @Mock
    private SoftixClient softixClient;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private BrandRepository brandRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private SoftixCatalogSyncService service() {
        return new SoftixCatalogSyncService(softixClient, productRepository, brandRepository, categoryRepository);
    }

    private void softix(ProductItem... items) {
        when(softixClient.listProducts(eq("MONTURA"), anyInt(), anyInt()))
                .thenReturn(new ProductPage(List.of(items), 1, items.length));
    }

    private void stock(StockItem... rows) {
        when(softixClient.listBranchStock("1")).thenReturn(rows.length == 0
                ? List.of(new StockItem("__TEST_SENTINEL__", null, null, 0, null, null, null))
                : List.of(rows));
        when(softixClient.listBranchStock("2")).thenReturn(List.of());
    }

    private static ProductItem item(String code, String brand, String reference, String color, String category,
            String name, double price) {
        return new ProductItem(code, name, brand, "MONTURA", color, reference, category,
                BigDecimal.valueOf(price), 1, null);
    }

    private static ProductItem item(String code, String brand, String reference, String color, String category,
            String name, BigDecimal price) {
        return new ProductItem(code, name, brand, "MONTURA", color, reference, category,
                price, 1, null);
    }

    private static Product local(String sku, String brand, String reference, String name, int stock, double price) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setReference(reference);
        product.setStock(stock);
        product.setPrice(BigDecimal.valueOf(price));
        product.setStatus(ProductStatus.ACTIVE);
        if (brand != null) {
            Brand b = new Brand();
            b.setName(brand);
            product.setBrand(b);
        }
        return product;
    }

    private void localProducts(Product... products) {
        when(productRepository.findAll()).thenReturn(List.of(products));
    }

    @Test
    void nuncaCreaProductosNuevos() {
        softix(item("M42", "RAYBAN", "R1", "Negro", "MONTURA", "M42", 100000));
        stock(new StockItem("M42", null, null, 2, null, null, null));
        localProducts();

        SyncResult result = service().sync();


        assertThat(result.updated()).isZero();
        verify(productRepository, never()).saveAll(any());
        verify(brandRepository, never()).save(any(Brand.class));
        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    void actualizaNombreMarcaCategoriaPrecioYStockRegistrandoValorAnteriorYNuevo() {
        softix(item("M1", "BOSS", "R1", "Negro", "SOL", "Nuevo nombre", 150000));
        stock(new StockItem("M1", null, null, 9, null, null, null));
        Product product = local("M1", "RAYBAN", "R1", "Viejo nombre", 4, 120000);
        Category old = new Category();
        old.setName("OPTICA");
        product.getCategories().add(old);
        localProducts(product);
        when(brandRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncResult result = service().sync();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.priceChanged()).isEqualTo(1);
        assertThat(result.stockChanged()).isEqualTo(1);
        assertThat(result.entries())
                .anySatisfy(e -> {
                    assertThat(e.field()).isEqualTo("nombre");
                    assertThat(e.oldValue()).isEqualTo("Viejo nombre");
                    assertThat(e.newValue()).isEqualTo("Nuevo nombre");
                })
                .anySatisfy(e -> {
                    assertThat(e.field()).isEqualTo("marca");
                    assertThat(e.oldValue()).isEqualTo("RAYBAN");
                    assertThat(e.newValue()).isEqualTo("BOSS");
                })
                .anySatisfy(e -> {
                    assertThat(e.field()).isEqualTo("categoría");
                    assertThat(e.newValue()).isEqualTo("SOL");
                })
                .anySatisfy(e -> {
                    assertThat(e.field()).isEqualTo("stock");
                    assertThat(e.oldValue()).isEqualTo("4");
                    assertThat(e.newValue()).isEqualTo("9");
                });
        assertThat(product.getName()).isEqualTo("Nuevo nombre");
        assertThat(product.getStock()).isEqualTo(9);
        verify(productRepository).saveAll(any());
    }

    @Test
    void skuSinReferenciaConservaSusDatosYRegistraError() {
        softix(item("M42", "RAYBAN", null, "Negro", "MONTURA", "Nuevo", 100000));
        stock(new StockItem("M42", null, null, 3, null, null, null));
        Product product = local("M42", "RAYBAN", "R42", "Anterior", 1, 90000);
        localProducts(product);

        SyncResult result = service().sync();

        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.entries()).singleElement()
                .satisfies(e -> {
                    assertThat(e.level()).isEqualTo("ERROR");
                    assertThat(e.message()).contains("Sin referencia", "Se conservan los datos anteriores");
                });
        assertThat(product.getName()).isEqualTo("Anterior");
        assertThat(product.getStock()).isEqualTo(1);
    }

    @Test
    void skuConPrecioNuloOCeroConservaSuPrecioAnteriorYRegistraError() {
        softix(item("M43", "RAYBAN", "R43", "Negro", "MONTURA", "Nuevo", (BigDecimal) null),
                item("M44", "RAYBAN", "R44", "Negro", "MONTURA", "Nuevo", BigDecimal.ZERO));
        stock();
        Product p43 = local("M43", "RAYBAN", "R43", "Anterior", 1, 90000);
        Product p44 = local("M44", "RAYBAN", "R44", "Anterior", 1, 80000);
        localProducts(p43, p44);

        SyncResult result = service().sync();

        assertThat(result.errors()).isEqualTo(2);
        assertThat(result.updated()).isZero();
        assertThat(p43.getPrice()).isEqualByComparingTo("90000");
        assertThat(p44.getPrice()).isEqualByComparingTo("80000");
    }

    @Test
    void referenciaRepetidaEnMarcasDistintasBloqueaAmbosSkus() {
        softix(item("M1", "RAYBAN", "R1", "Negro", "MONTURA", "A", 1000),
                item("M2", "BOSS", "R1", "Negro", "MONTURA", "B", 1000));
        stock();
        localProducts(local("M1", "RAYBAN", "R1", "A", 1, 1000), local("M2", "BOSS", "R1", "B", 1, 1000));

        SyncResult result = service().sync();

        assertThat(result.errors()).isEqualTo(2);
        assertThat(result.updated()).isZero();
    }

    @Test
    void duplicadoDeMarcaReferenciaYColorBloqueaAmbosSkus() {
        softix(item("M897", "RAYBAN", "R1", "Negro", "MONTURA", "A", 1000),
                item("M898", "RAYBAN", "R1", "Negro", "MONTURA", "B", 1000));
        stock();
        localProducts(local("M897", "RAYBAN", "R1", "A", 1, 1000), local("M898", "RAYBAN", "R1", "B", 1, 1000));

        SyncResult preview = service().preview();

        assertThat(preview.errors()).isEqualTo(2);
        assertThat(preview.entries()).allSatisfy(e -> assertThat(e.message()).contains("Duplicado"));
    }

    @Test
    void ocultaLosProductosQueYaNoEstanEnSoftix() {
        softix(item("OTHER", "RAYBAN", "R0", "Negro", "MONTURA", "Otro", 1000));
        stock();
        Product product = local("M9", "RAYBAN", "R9", "Viejo", 2, 1000);
        localProducts(product);

        SyncResult result = service().sync();

        assertThat(result.hidden()).isEqualTo(1);
        assertThat(result.entries()).singleElement()
                .satisfies(e -> {
                    assertThat(e.field()).isEqualTo("softixMissing");
                    assertThat(e.newValue()).isEqualTo("true");
                });
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.getSoftixMissing()).isTrue();
    }

    @Test
    void vistaPreviaNoEscribeNada() {
        softix(item("M1", "RAYBAN", "R1", "Negro", "MONTURA", "Nuevo", 200000));
        stock(new StockItem("M1", null, null, 5, null, null, null));
        localProducts(local("M1", "RAYBAN", "R1", "Viejo", 1, 100000));

        SyncResult result = service().preview();

        assertThat(result.updated()).isEqualTo(1);
        verify(productRepository, never()).saveAll(any());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    @Test
    void importaProductosValidosActivosYDevuelveMarcasYCategoriasCreadas() {
        softix(item("M1", "RAY BAN", "R1", "Negro", "MONTURAS", "Nuevo", 150000));
        stock(new StockItem("M1", null, null, 4, null, null, null));
        localProducts();
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().importNewProducts(Set.of(), Set.of());

        assertThat(result.createdSkus()).containsExactly("M1");
        assertThat(result.createdBrands()).containsExactly("RAY BAN");
        assertThat(result.createdCategories()).containsExactly("MONTURAS");
        assertThat(result.newSkus()).singleElement()
                .satisfies(sku -> assertThat(sku.state()).isEqualTo(SoftixCatalogSyncService.NewSkuState.VALID));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Product>> captor = ArgumentCaptor.forClass(List.class);
        verify(productRepository).saveAll(captor.capture());
        Product created = captor.getValue().getFirst();
        assertThat(created.getSku()).isEqualTo("M1");
        assertThat(created.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.getSoftixMissing()).isFalse();
        assertThat(created.getStock()).isEqualTo(4);
        assertThat(created.getPrice()).isEqualByComparingTo("150000");
        assertThat(created.getSaleVat()).isTrue();
    }

    @Test
    void skuSinMarcaSeBloqueaYNoSeImporta() {
        softix(item("M2", null, "R2", "Negro", "MONTURAS", "Sin marca", 100000));
        stock();
        localProducts();

        var result = service().importNewProducts(Set.of(), Set.of());

        assertThat(result.createdSkus()).isEmpty();
        assertThat(result.newSkus()).singleElement().satisfies(sku -> {
            assertThat(sku.state()).isEqualTo(SoftixCatalogSyncService.NewSkuState.BLOCKED);
            assertThat(sku.blockReason()).contains("Sin marca");
        });
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void precioNuloOCeroBloqueaElSku() {
        softix(item("M3", "RAY BAN", "R3", "Negro", "MONTURAS", "Sin precio", (BigDecimal) null),
                item("M4", "RAY BAN", "R4", "Negro", "MONTURAS", "Precio cero", BigDecimal.ZERO));
        stock();
        localProducts();

        var result = service().importNewProducts(Set.of(), Set.of());

        assertThat(result.createdSkus()).isEmpty();
        assertThat(result.newSkus()).hasSize(2)
                .allSatisfy(sku -> {
                    assertThat(sku.state()).isEqualTo(SoftixCatalogSyncService.NewSkuState.BLOCKED);
                    assertThat(sku.blockReason()).contains("Sin precio");
                });
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void skuBloqueadoEnMarcaIgnoradaQuedaIgnoradoYNoCuentaComoBloqueado() {
        softix(item("M5", "RAY BAN", null, "Negro", "MONTURAS", "Sin referencia", 100000));
        stock();
        localProducts();

        var result = service().importNewProducts(Set.of("RAY BAN"), Set.of());

        assertThat(result.newSkus()).singleElement()
                .satisfies(sku -> assertThat(sku.state())
                        .isEqualTo(SoftixCatalogSyncService.NewSkuState.IGNORED_BY_BRAND));
        assertThat(result.blocked()).isEmpty();
    }

    @Test
    void skuBloqueadoIgnoradoIndividualmenteQuedaIgnorado() {
        softix(item("M6", "RAY BAN", null, "Negro", "MONTURAS", "Sin referencia", 100000));
        stock();
        localProducts();

        var result = service().importNewProducts(Set.of(), Set.of("M6"));

        assertThat(result.newSkus()).singleElement()
                .satisfies(sku -> assertThat(sku.state())
                        .isEqualTo(SoftixCatalogSyncService.NewSkuState.IGNORED_BY_SKU));
        assertThat(result.blocked()).isEmpty();
    }

    @Test
    void identidadExactaDistingueMayusculasYCreaMarcaYCategoriaNuevas() {
        softix(item("M7", "RAY BAN", "R7", "Negro", "MONTURAS", "Nuevo", 100000));
        stock();
        localProducts();
        Brand existingBrand = new Brand();
        existingBrand.setName("Ray Ban");
        Category existingCategory = new Category();
        existingCategory.setName("Monturas");
        when(brandRepository.findAll()).thenReturn(List.of(existingBrand));
        when(categoryRepository.findAll()).thenReturn(List.of(existingCategory));
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().importNewProducts(Set.of("Ray Ban"), Set.of());

        assertThat(result.newSkus()).singleElement()
                .satisfies(sku -> assertThat(sku.state()).isEqualTo(SoftixCatalogSyncService.NewSkuState.VALID));
        assertThat(result.createdBrands()).containsExactly("RAY BAN");
        assertThat(result.createdCategories()).containsExactly("MONTURAS");
    }

    @Test
    void previewDeImportacionSoloMuestraMarcasYCategoriasQueSeCrearan() {
        softix(item("M8", "NUEVA", null, "Negro", "MONTURAS", "Bloqueado", 100000),
                item("M9", "OTRA", "R9", "Negro", "MONTURAS", "Válido", 100000));
        stock();
        localProducts();

        var preview = service().previewImport(Set.of(), Set.of());

        assertThat(preview.brandsToCreate()).containsExactly("OTRA");
        assertThat(preview.categoriesToCreate()).containsExactly("MONTURAS");
        assertThat(preview.newSkus()).extracting(SoftixCatalogSyncService.NewSku::state)
                .containsExactly(SoftixCatalogSyncService.NewSkuState.BLOCKED,
                        SoftixCatalogSyncService.NewSkuState.VALID);
    }

    @Test
    void referenciasConMayusculasDistintasNoSonDuplicado() {
        softix(item("M10", "RAY BAN", "R1", "Negro", "MONTURAS", "A", 1000),
                item("M11", "RAY BAN", "r1", "Negro", "MONTURAS", "B", 1000));
        stock();
        localProducts();
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service().importNewProducts(Set.of(), Set.of());

        assertThat(result.newSkus()).allSatisfy(sku ->
                assertThat(sku.state()).isEqualTo(SoftixCatalogSyncService.NewSkuState.VALID));
    }

    @Test
    void laVistaPreviaMuestraCadaMarcaYCategoriaNuevaUnaSolaVez() {
        softix(item("M12", "NUEVA", "R12", "Negro", "CAT", "A", 1000),
                item("M13", "NUEVA", "R13", "Negro", "CAT", "B", 1000));
        stock();
        localProducts(local("M12", "VIEJA", "R12", "A", 1, 1000),
                local("M13", "VIEJA", "R13", "B", 1, 1000));
        when(brandRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());

        SyncResult result = service().preview();

        assertThat(result.entries().stream()
                .filter(e -> "Marca nueva".equals(e.message())))
                .hasSize(1);
        assertThat(result.entries().stream()
                .filter(e -> "Categoría nueva".equals(e.message())))
                .hasSize(1);
    }

    @Test
    void productoOcultoManualmentePierdeRespaldoSinCambiarEstado() {
        softix(item("OTHER", "RAYBAN", "R0", "Negro", "MONTURA", "Otro", 1000));
        stock();
        Product product = local("M20", "RAYBAN", "R20", "Viejo", 2, 1000);
        product.setStatus(ProductStatus.HIDDEN);
        localProducts(product);

        SyncResult result = service().sync();

        assertThat(result.hidden()).isEqualTo(1);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.getSoftixMissing()).isTrue();
    }

    @Test
    void productoQueReapareceRecuperaRespaldoSinCambiarEstado() {
        softix(item("M21", "RAYBAN", "R21", "Negro", "MONTURA", "Producto", 1000));
        stock(new StockItem("M21", null, null, 2, null, null, null));
        Product product = local("M21", "RAYBAN", "R21", "Anterior", 1, 900);
        product.setStatus(ProductStatus.HIDDEN);
        product.setSoftixMissing(true);
        localProducts(product);
        when(brandRepository.findAll()).thenReturn(List.of());
        when(categoryRepository.findAll()).thenReturn(List.of());
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SyncResult result = service().sync();

        assertThat(result.entries()).anySatisfy(e -> assertThat(e.action()).isEqualTo(SyncEventAction.RESTORED));
        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.getSoftixMissing()).isFalse();
    }

    @Test
    void catalogoVacioFallaSinOcultarProductos() {
        softix();
        stock();

        assertThatThrownBy(() -> service().sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catálogo vacío");
        verify(productRepository, never()).saveAll(any());
    }

    @Test
    void stockVacioFallaSinPonerStockEnCero() {
        softix(item("M23", "RAYBAN", "R23", "Negro", "MONTURA", "Producto", 1000));
        when(softixClient.listBranchStock("1")).thenReturn(List.of());
        when(softixClient.listBranchStock("2")).thenReturn(List.of());

        assertThatThrownBy(() -> service().sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stock vacío");
        verify(productRepository, never()).saveAll(any());
    }
}
