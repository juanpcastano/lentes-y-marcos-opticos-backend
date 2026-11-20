package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.AvailabilityEnvelope;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.ProductPage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.StockItem;

class SoftixDtosTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void leeDisponibilidadDentroDelSobre() throws Exception {
        String json = """
                {"status":{"code":200,"message":"Query executed successfully","success":true},
                 "data":{"productCode":"M628","availableQuantity":27}}
                """;

        AvailabilityEnvelope envelope = mapper.readValue(json, AvailabilityEnvelope.class);

        assertThat(envelope.data().productCode()).isEqualTo("M628");
        assertThat(envelope.data().availableQuantity()).isEqualTo(27);
    }

    @Test
    void leeFilasDeStockPorSede() throws Exception {
        String json = """
                [{"cantidad":2,"codigo":"M100","descripcion":"WONDERTH 68043","tipoProducto":"MONTURA",
                  "maxDescuento":10,"valor":130000,"valor2":0}]
                """;

        List<StockItem> items = mapper.readValue(json,
                mapper.getTypeFactory().constructCollectionType(List.class, StockItem.class));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.codigo()).isEqualTo("M100");
            assertThat(item.cantidad()).isEqualTo(2);
            assertThat(item.valor()).isEqualByComparingTo("130000");
        });
    }

    @Test
    void leeCatalogoConPrecioDeVenta() throws Exception {
        String json = """
                {"page":1,"pageSize":1,"totalPages":2253,"totalRecords":2253,"url":null,
                 "products":[{"code":"M628","name":"BARTOLINI BA113501","brand":"BARTOLINI",
                   "productType":"MONTURA","color":"VARIOS","salePrice":270000,"deleted":false}]}
                """;

        ProductPage page = mapper.readValue(json, ProductPage.class);

        assertThat(page.totalRecords()).isEqualTo(2253);
        assertThat(page.products()).singleElement().satisfies(product -> {
            assertThat(product.code()).isEqualTo("M628");
            assertThat(product.salePrice()).isEqualByComparingTo("270000");
        });
    }
}
