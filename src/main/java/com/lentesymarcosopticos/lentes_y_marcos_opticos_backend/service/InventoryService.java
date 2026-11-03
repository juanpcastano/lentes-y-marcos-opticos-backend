package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryFrameResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.InventoryItem;

@Service
public class InventoryService {
    private final SoftixClient softixClient;
    private final List<String> branchIds;

    public InventoryService(SoftixClient softixClient,
            @Value("${softix.branch-ids}") String configuredBranchIds) {
        this.softixClient = softixClient;
        this.branchIds = Arrays.stream(configuredBranchIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public List<InventoryFrameResponse> listFrames(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La página debe ser positiva y pageSize debe estar entre 1 y 100");
        }
        if (branchIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No hay sedes Softix configuradas para consultar inventario");
        }

        Map<String, InventoryItem> merged = new LinkedHashMap<>();
        try {
            for (String branchId : branchIds) {
                List<InventoryItem> items = softixClient.listFrames(branchId, page, pageSize).glassesFrames();
                if (items == null) {
                    items = Collections.emptyList();
                }
                for (InventoryItem item : items) {
                    merged.merge(item.code(), item, this::mergeItems);
                }
            }
            return merged.values().stream()
                    .map(this::toResponse)
                    .toList();
        } catch (SoftixClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No fue posible consultar el inventario en Softix");
        }
    }

    private InventoryItem mergeItems(InventoryItem first, InventoryItem second) {
        if (first.price() != null && second.price() != null
                && first.price().compareTo(second.price()) != 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Softix devolvió precios diferentes para el SKU " + first.code());
        }
        int firstQuantity = first.quantity() == null ? 0 : first.quantity();
        int secondQuantity = second.quantity() == null ? 0 : second.quantity();
        return new InventoryItem(
                first.code(), first.description(), first.productType(),
                firstQuantity + secondQuantity,
                first.price() != null ? first.price() : second.price(),
                first.price2() != null ? first.price2() : second.price2(),
                first.maxDiscount() != null ? first.maxDiscount() : second.maxDiscount());
    }

    private InventoryFrameResponse toResponse(InventoryItem item) {
        return new InventoryFrameResponse(
                item.code(), item.description(), item.quantity(), item.price(), item.maxDiscount());
    }
}
