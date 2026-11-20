package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryAvailabilityResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.InventoryService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/inventory")
@AllArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/availability/{sku}")
    public ResponseEntity<InventoryAvailabilityResponse> availability(@PathVariable String sku) {
        return ResponseEntity.ok(inventoryService.availability(sku));
    }
}
