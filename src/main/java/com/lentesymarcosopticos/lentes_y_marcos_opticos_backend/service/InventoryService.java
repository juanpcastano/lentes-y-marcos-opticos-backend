package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryAvailabilityResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.AvailabilityData;

@Service
public class InventoryService {
    private final SoftixClient softixClient;

    public InventoryService(SoftixClient softixClient) {
        this.softixClient = softixClient;
    }

    public InventoryAvailabilityResponse availability(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El SKU es obligatorio");
        }
        try {
            AvailabilityData data = softixClient.getAvailability(sku);
            if (data == null || data.availableQuantity() == null) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Softix no devolvió la disponibilidad del SKU " + sku);
            }
            return new InventoryAvailabilityResponse(sku, Math.max(data.availableQuantity(), 0));
        } catch (SoftixClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No fue posible consultar la disponibilidad en Softix");
        }
    }
}
