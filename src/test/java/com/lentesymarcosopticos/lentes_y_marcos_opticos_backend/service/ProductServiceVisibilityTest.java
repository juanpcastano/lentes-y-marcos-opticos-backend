package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.ProductDetailDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Brand;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.Product;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductStatus;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceVisibilityTest {

	@Mock
	private ProductRepository productRepository;

	private ProductService productService;

	@BeforeEach
	void setUp() {
		productService = new ProductService(productRepository);
	}

	@Test
	void productoActivoSinRespaldoResponde404() {
		Product product = product(ProductStatus.ACTIVE, 5, true);
		when(productRepository.findDetailById(product.getId())).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> productService.getProductById(product.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void productoOcultoResponde404() {
		Product product = product(ProductStatus.HIDDEN, 5, false);
		when(productRepository.findDetailById(product.getId())).thenReturn(Optional.of(product));

		assertThatThrownBy(() -> productService.getProductById(product.getId()))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void productoNoActivoSigueAccesiblePorUrlDirecta() {
		Product product = product(ProductStatus.INACTIVE, 0, false);
		when(productRepository.findDetailById(product.getId())).thenReturn(Optional.of(product));

		ProductDetailDto detail = productService.getProductById(product.getId());

		assertThat(detail.status()).isEqualTo(ProductStatus.INACTIVE);
		assertThat(detail.available()).isFalse();
	}

	@Test
	void otrosColoresExcluyeSinRespaldoYAgotadosNoDisponibles() {
		Product base = product(ProductStatus.ACTIVE, 5, false);
		base.setReference("VR2411");

		Product disponible = product(ProductStatus.ACTIVE, 3, false);
		disponible.setReference("VR2411");

		Product sinRespaldo = product(ProductStatus.ACTIVE, 5, true);
		sinRespaldo.setReference("VR2411");

		Product agotado = product(ProductStatus.ACTIVE, 0, false);
		agotado.setReference("VR2411");

		when(productRepository.findDetailById(base.getId())).thenReturn(Optional.of(base));
		when(productRepository.findByBrand_IdAndReferenceAndIdNot(
				base.getBrand().getId(), base.getReference(), base.getId()))
				.thenReturn(List.of(disponible, sinRespaldo, agotado));

		ProductDetailDto detail = productService.getProductById(base.getId());

		assertThat(detail.otherColors()).hasSize(2);
		assertThat(detail.otherColors())
				.extracting(ProductDetailDto.OtherColorDto::id)
				.containsExactly(disponible.getId(), agotado.getId());
		assertThat(detail.otherColors().get(0).available()).isTrue();
		assertThat(detail.otherColors().get(1).available()).isFalse();
	}

	private static Product product(ProductStatus status, int stock, boolean softixMissing) {
		Product product = new Product();
		product.setId(UUID.randomUUID());
		product.setSku(UUID.randomUUID().toString());
		product.setName("Producto");
		product.setBrand(brand());
		product.setStatus(status);
		product.setStock(stock);
		product.setSoftixMissing(softixMissing);
		return product;
	}

	private static Brand brand() {
		Brand brand = new Brand();
		brand.setId(UUID.randomUUID());
		brand.setName("Marca");
		return brand;
	}
}
