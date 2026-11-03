package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.InventoryItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.InventoryPage;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    private SoftixClient softixClient;

    @Test
    void transformaInventarioDeSoftixSinExponerRespuestaCruda() {
        when(softixClient.listFrames("1", 1, 50)).thenReturn(new InventoryPage(List.of(
                new InventoryItem("M100", "Montura azul", "MONTURA", 5,
                        new BigDecimal("64663.87"), null, new BigDecimal("10")))));

        InventoryService service = new InventoryService(softixClient, "1");

        var frames = service.listFrames(1, 50);

        assertThat(frames).hasSize(1);
        assertThat(frames.getFirst().sku()).isEqualTo("M100");
        assertThat(frames.getFirst().quantity()).isEqualTo(5);
        assertThat(frames.getFirst().price()).isEqualByComparingTo("64663.87");
        verify(softixClient).listFrames("1", 1, 50);
    }

    @Test
    void fusionaCantidadDeLasDosSedesTecnicas() {
        when(softixClient.listFrames("1", 1, 50)).thenReturn(new InventoryPage(List.of(
                new InventoryItem("M100", "Montura azul", "MONTURA", 2,
                        new BigDecimal("100"), null, null))));
        when(softixClient.listFrames("2", 1, 50)).thenReturn(new InventoryPage(List.of(
                new InventoryItem("M100", "Montura azul", "MONTURA", 3,
                        new BigDecimal("100"), null, null))));

        InventoryService service = new InventoryService(softixClient, "1,2");

        var frames = service.listFrames(1, 50);

        assertThat(frames).singleElement().extracting(frame -> frame.quantity()).isEqualTo(5);
    }
}
