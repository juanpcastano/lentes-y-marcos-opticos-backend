package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.MediaAssetDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.MediaReferenceDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.ProductImage;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.BrandRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.CategoryRepository;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.ProductRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class AdminMediaService {

	private static final List<String> FOLDERS = List.of("products", "brands", "categories");

	private final StorageService storageService;
	private final BrandRepository brandRepository;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public List<MediaAssetDto> listGallery() {
		Map<String, List<MediaReferenceDto>> references = references();
		return FOLDERS.stream()
				.flatMap(folder -> storageService.list(folder).stream())
				.map(object -> new MediaAssetDto(object.key(), object.url(), folderOf(object.key()),
						references.getOrDefault(object.key(), List.of())))
				.sorted(Comparator.comparing(MediaAssetDto::used).thenComparing(MediaAssetDto::key))
				.toList();
	}

	@Transactional
	public void delete(String key, boolean force) {
		if (!isAllowedKey(key)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "Clave de imagen no permitida");
		}
		Map<String, List<MediaReferenceDto>> references = references();
		List<MediaReferenceDto> usedBy = references.getOrDefault(key, List.of());
		if (!usedBy.isEmpty() && !force) {
			throw new ApiException(HttpStatus.CONFLICT,
					"La imagen está siendo utilizada por " + usedBy.size() + " entidad(es)");
		}
		removeReferences(key);
		storageService.deleteKey(key);
	}

	private Map<String, List<MediaReferenceDto>> references() {
		Map<String, List<MediaReferenceDto>> result = new HashMap<>();
		brandRepository.findAll().forEach(brand -> add(result, brand.getImageUrl(),
				new MediaReferenceDto("brand", brand.getId().toString(), brand.getName())));
		categoryRepository.findAll().forEach(category -> add(result, category.getImageUrl(),
				new MediaReferenceDto("category", category.getId().toString(), category.getName())));
		productRepository.findAllWithImages().forEach(product -> product.getImages().forEach(image ->
				add(result, image.getImageUrl(), new MediaReferenceDto("product", product.getId().toString(), product.getName()))));
		productRepository.findAllWithVariants().forEach(product -> product.getVariants().forEach(variant ->
				add(result, variant.getImageUrl(), new MediaReferenceDto("variant", product.getId().toString(),
						product.getName() + " / " + variant.getVariantName()))));
		return result;
	}

	private void removeReferences(String key) {
		brandRepository.findAll().forEach(brand -> {
			if (key.equals(storageService.keyForUrl(brand.getImageUrl()))) {
				brand.setImageUrl(null);
			}
		});
		categoryRepository.findAll().forEach(category -> {
			if (key.equals(storageService.keyForUrl(category.getImageUrl()))) {
				category.setImageUrl(null);
			}
		});
		productRepository.findAllWithImages().forEach(product -> {
			List<ProductImage> removed = product.getImages().stream()
					.filter(image -> key.equals(storageService.keyForUrl(image.getImageUrl())))
					.toList();
			boolean primaryRemoved = removed.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsPrimary()));
			product.getImages().removeAll(removed);
			if (primaryRemoved) {
				product.getImages().stream()
						.min(Comparator.comparing(image -> image.getSortOrder() == null ? Integer.MAX_VALUE : image.getSortOrder()))
						.ifPresent(image -> image.setIsPrimary(true));
			}
		});
		productRepository.findAllWithVariants().forEach(product -> product.getVariants().forEach(variant -> {
			if (key.equals(storageService.keyForUrl(variant.getImageUrl()))) {
				variant.setImageUrl(null);
			}
		}));
	}

	private void add(Map<String, List<MediaReferenceDto>> references, String url, MediaReferenceDto reference) {
		String key = storageService.keyForUrl(url);
		if (key != null) {
			references.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference);
		}
	}

	private boolean isAllowedKey(String key) {
		return key != null && FOLDERS.stream().anyMatch(folder -> key.startsWith(folder + "/"))
				&& !key.contains("..") && !key.endsWith("/");
	}

	private String folderOf(String key) {
		return key.substring(0, key.indexOf('/'));
	}
}
