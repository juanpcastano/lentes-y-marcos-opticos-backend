package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Category;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
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
        when(softixClient.listBranchStock("1")).thenReturn(List.of(rows));
        when(softixClient.listBranchStock("2")).thenReturn(List.of());
    }

    private static ProductItem item(String code, String brand, String reference, String color, String category,
            String name, double price) {
        return new ProductItem(code, name, brand, "MONTURA", color, reference, category,
                BigDecimal.valueOf(price), 1, null);
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
        softix();
        stock();
        localProducts(local("M9", "RAYBAN", "R9", "Viejo", 2, 1000));

        SyncResult result = service().sync();

        assertThat(result.hidden()).isEqualTo(1);
        assertThat(result.entries()).singleElement()
                .satisfies(e -> {
                    assertThat(e.field()).isEqualTo("estado");
                    assertThat(e.newValue()).isEqualTo("HIDDEN");
                });
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
}
