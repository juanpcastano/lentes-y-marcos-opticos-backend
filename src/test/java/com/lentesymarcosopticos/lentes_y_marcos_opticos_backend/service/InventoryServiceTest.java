package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.AvailabilityData;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock
    private SoftixClient softixClient;

    @Test
    void devuelveLaDisponibilidadTotalDelSku() {
        when(softixClient.getAvailability("M628")).thenReturn(new AvailabilityData("M628", 27));

        var response = new InventoryService(softixClient).availability("M628");

        assertThat(response.sku()).isEqualTo("M628");
        assertThat(response.availableQuantity()).isEqualTo(27);
        verify(softixClient).getAvailability("M628");
    }

    @Test
    void codigoSinStockDevuelveCero() {
        when(softixClient.getAvailability("M999999")).thenReturn(new AvailabilityData("M999999", 0));

        var response = new InventoryService(softixClient).availability("M999999");

        assertThat(response.availableQuantity()).isZero();
    }

    @Test
    void respuestaSinCantidadSeTrataComoError() {
        when(softixClient.getAvailability("M1")).thenReturn(new AvailabilityData("M1", null));

        assertThatThrownBy(() -> new InventoryService(softixClient).availability("M1"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void errorDeSoftixSeConvierteEnErrorDeGateway() {
        when(softixClient.getAvailability("M1")).thenThrow(new SoftixClientException("caída"));

        assertThatThrownBy(() -> new InventoryService(softixClient).availability("M1"))
                .isInstanceOf(ApiException.class);
    }
}
