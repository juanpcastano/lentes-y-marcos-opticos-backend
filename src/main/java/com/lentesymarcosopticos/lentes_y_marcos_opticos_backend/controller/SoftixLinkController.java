package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service.SoftixLinkService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/users/me/softix")
@AllArgsConstructor
@Validated
public class SoftixLinkController {
    private final SoftixLinkService softixLinkService;

    @PostMapping("/link")
    public ResponseEntity<SoftixLinkResponse> link(@Valid @RequestBody SoftixLinkRequest request) {
        return ResponseEntity.ok(softixLinkService.link(request));
    }

    @PostMapping("/create")
    public ResponseEntity<SoftixLinkResponse> createAndLink(
            @Valid @RequestBody SoftixCreateLinkRequest request) {
        return ResponseEntity.ok(softixLinkService.createAndLink(request));
    }
}
