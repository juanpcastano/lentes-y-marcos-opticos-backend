package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceDeleteTest {
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StorageService storageService;

    @Test
    void eliminaElProductoExistente() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        when(productRepository.findDetailById(id)).thenReturn(Optional.of(product));

        new AdminProductService(productRepository, storageService).delete(id);

        verify(productRepository).delete(product);
        verifyNoInteractions(storageService);
    }

    @Test
    void productoInexistenteResponde404SinBorrar() {
        UUID id = UUID.randomUUID();
        when(productRepository.findDetailById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AdminProductService(productRepository, storageService).delete(id))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(productRepository, never()).delete(any(Product.class));
    }
}
