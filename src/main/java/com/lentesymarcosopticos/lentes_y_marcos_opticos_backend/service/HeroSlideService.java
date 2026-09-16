package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.HeroSlideDto;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.HeroSlideRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.HeroSlide;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.HeroSlideRepository;

import lombok.AllArgsConstructor;

/**
 * HeroSlideService — hero de la página principal.
 */
@Service
@AllArgsConstructor
public class HeroSlideService {

	private final HeroSlideRepository heroSlideRepository;

	@Transactional(readOnly = true)
	public List<HeroSlideDto> getActiveSlides() {
		return heroSlideRepository.findByIsActiveTrueOrderBySortOrderAsc().stream()
				.filter(slide -> slide.getImageUrl() != null && !slide.getImageUrl().isBlank())
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<HeroSlideDto> listAll() {
		return heroSlideRepository.findAllByOrderBySortOrderAsc().stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional
	public HeroSlideDto create(HeroSlideRequest request) {
		validateCtas(request);
		HeroSlide slide = new HeroSlide();
		slide.setTitle(request.title().trim());
		slide.setDescription(blankToNull(request.description()));
		slide.setImageUrl(request.imageUrl().trim());
		slide.setCtaLabel(blankToNull(request.ctaLabel()));
		slide.setCtaTo(blankToNull(request.ctaTo()));
		slide.setCta2Label(blankToNull(request.cta2Label()));
		slide.setCta2To(blankToNull(request.cta2To()));
		slide.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
		slide.setIsActive(request.isActive() == null || request.isActive());
		return toDto(heroSlideRepository.save(slide));
	}

	@Transactional
	public HeroSlideDto update(UUID id, HeroSlideRequest request) {
		validateCtas(request);
		HeroSlide slide = heroSlideRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Slide no encontrado"));
		slide.setTitle(request.title().trim());
		slide.setDescription(blankToNull(request.description()));
		slide.setImageUrl(request.imageUrl().trim());
		slide.setCtaLabel(blankToNull(request.ctaLabel()));
		slide.setCtaTo(blankToNull(request.ctaTo()));
		slide.setCta2Label(blankToNull(request.cta2Label()));
		slide.setCta2To(blankToNull(request.cta2To()));
		if (request.sortOrder() != null) {
			slide.setSortOrder(request.sortOrder());
		}
		if (request.isActive() != null) {
			slide.setIsActive(request.isActive());
		}
		return toDto(slide);
	}

	@Transactional
	public void delete(UUID id) {
		HeroSlide slide = heroSlideRepository.findById(id)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Slide no encontrado"));
		heroSlideRepository.delete(slide);
	}

	@Transactional
	public List<HeroSlideDto> reorder(List<UUID> ids) {
		List<HeroSlide> slides = heroSlideRepository.findAllById(ids);
		if (slides.size() != ids.size()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "Algún slide no existe");
		}
		for (int i = 0; i < ids.size(); i++) {
			UUID id = ids.get(i);
			HeroSlide slide = slides.stream()
					.filter(s -> s.getId().equals(id))
					.findFirst()
					.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Slide no encontrado"));
			slide.setSortOrder(i);
		}
		return heroSlideRepository.findAllByOrderBySortOrderAsc().stream()
				.map(this::toDto)
				.toList();
	}

	private void validateCtas(HeroSlideRequest request) {
		validateCtaPair(request.ctaLabel(), request.ctaTo(), "principal");
		validateCtaPair(request.cta2Label(), request.cta2To(), "secundaria");
	}

	private void validateCtaPair(String label, String to, String which) {
		boolean hasLabel = label != null && !label.isBlank();
		boolean hasTo = to != null && !to.isBlank();
		if (hasLabel != hasTo) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"La acción " + which + " necesita etiqueta y destino a la vez");
		}
	}

	private String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private HeroSlideDto toDto(HeroSlide slide) {
		List<HeroSlideDto.HeroSlideActionDto> actions = new ArrayList<>();
		if (slide.getCtaLabel() != null && slide.getCtaTo() != null) {
			actions.add(new HeroSlideDto.HeroSlideActionDto(slide.getCtaLabel(), slide.getCtaTo()));
		}
		if (slide.getCta2Label() != null && slide.getCta2To() != null) {
			actions.add(new HeroSlideDto.HeroSlideActionDto(slide.getCta2Label(), slide.getCta2To()));
		}
		return new HeroSlideDto(
				slide.getId(),
				slide.getTitle(),
				slide.getDescription(),
				slide.getImageUrl(),
				actions,
				slide.getSortOrder(),
				slide.getIsActive());
	}
}
