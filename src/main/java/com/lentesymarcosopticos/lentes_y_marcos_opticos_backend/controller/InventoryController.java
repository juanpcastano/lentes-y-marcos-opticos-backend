package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.InventoryFrameResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.InventoryService;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/inventory")
@AllArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/frames")
    public ResponseEntity<List<InventoryFrameResponse>> listFrames(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseEntity.ok(inventoryService.listFrames(page, pageSize));
    }
}
