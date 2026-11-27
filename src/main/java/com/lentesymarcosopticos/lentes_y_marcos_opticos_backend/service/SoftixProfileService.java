package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;

import lombok.AllArgsConstructor;

/**
 * Lectura y edición de la ficha de un usuario ya autenticado. La vinculación y
 * creación iniciales pertenecen a {@link RegistrationService}; aquí solo se
 * edita la lista blanca de datos personales.
 */
@Service
@AllArgsConstructor
public class SoftixProfileService {

	private final AuthService authService;
	private final SoftixCustomerGateway gateway;

	@Transactional(readOnly = true)
	public SoftixProfileResponse getProfile() {
		User user = requireLinked();
		return gateway.toProfile(gateway.fetchCustomer(user.getSoftixClientId()));
	}

	@Transactional
	public SoftixProfileResponse updateProfile(SoftixProfileUpdateRequest request) {
		User user = requireLinked();
		CustomerResponse current = gateway.fetchCustomer(user.getSoftixClientId());
		CustomerCreateRequest body = gateway.merge(current, request, user.getPhoneE164(),
				user.getConsentAt() != null);
		gateway.updateCustomer(current.documentId(), body);
		return gateway.toProfile(body);
	}

	@Transactional(readOnly = true)
	public List<DocumentTypeResponse> documentTypes() {
		return gateway.documentTypes();
	}

	private User requireLinked() {
		User user = authService.currentUser();
		if (user.getSoftixClientId() == null) {
			throw new ApiException(HttpStatus.CONFLICT,
					"Tu cuenta todavía no está vinculada.");
		}
		return user;
	}
}
