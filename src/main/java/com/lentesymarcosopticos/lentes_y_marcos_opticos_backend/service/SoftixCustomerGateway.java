package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.DocumentTypeResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateCustomerRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixProfileUpdateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.SoftixCustomerIndex;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.DocumentTypeItem;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.SoftixCustomerIndexRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Operaciones contra Softix para la ficha del cliente. No conoce la sesión ni
 * el usuario autenticado: recibe el teléfono verificado y devuelve datos ya
 * filtrados para el frontend.
 */
@Service
@Slf4j
public class SoftixCustomerGateway {

	private static final String SOFTIX_REGIME = "49";
	private static final String SOFTIX_PRICE_LIST = "1";
	private static final String SOFTIX_COUNTRY = "CO";
	private static final String SOFTIX_PERSON_TYPE = "Natural";
	private static final String SOFTIX_NO_TAX_RESPONSIBILITY = "ZZ";
	private static final String SOFTIX_BLOOD_TYPE_UNKNOWN = "NE";
	private static final int SOFTIX_AUTHORIZATION_GRANTED = 1;
	private static final String DEFAULT_CITY = "76";
	private static final String DEFAULT_LOCALITY = "001";
	/** Tipos aceptados en el autoservicio, en el orden en que se muestran. */
	private static final List<String> CUSTOMER_DOCUMENT_TYPES = List.of("CC", "CE", "TI", "PA");
	private static final Map<String, String> CUSTOMER_DOCUMENT_TYPE_NAMES = Map.of(
			"CC", "Cédula de ciudadanía",
			"CE", "Cédula de extranjería",
			"TI", "Tarjeta de identidad",
			"PA", "Pasaporte");

	private final SoftixClient softixClient;
	private final SoftixCustomerIndexRepository indexRepository;
	private final PhoneNormalizer phoneNormalizer;
	private final String creationBranch;
	private final String createdBy;
	private final String placeholderEmailDomain;

	public SoftixCustomerGateway(SoftixClient softixClient,
			SoftixCustomerIndexRepository indexRepository,
			PhoneNormalizer phoneNormalizer,
			@Value("${softix.creation-branch:1}") String creationBranch,
			@Value("${softix.created-by:1}") String createdBy,
			@Value("${softix.placeholder-email-domain:sin-correo.invalid}") String placeholderEmailDomain) {
		this.softixClient = softixClient;
		this.indexRepository = indexRepository;
		this.phoneNormalizer = phoneNormalizer;
		this.creationBranch = creationBranch;
		this.createdBy = createdBy;
		this.placeholderEmailDomain = placeholderEmailDomain;
	}

	public CustomerResponse fetchCustomer(String documentId) {
		try {
			CustomerResponse customer = softixClient.findCustomer(documentId);
			if (customer == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"No pudimos consultar tus datos. Intenta de nuevo.");
			}
			return customer;
		} catch (SoftixClientException ex) {
			if (isCustomerNotFound(ex)) {
				throw new ApiException(HttpStatus.NOT_FOUND,
						"No encontramos tus datos. Contáctanos.");
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No fue posible consultar tus datos");
		}
	}

	public void createCustomer(CustomerCreateRequest request) {
		try {
			softixClient.createCustomer(request);
		} catch (SoftixClientException ex) {
			if (ex.getStatusCode() != null && ex.getStatusCode() == 400) {
				throw new ApiException(HttpStatus.BAD_REQUEST,
						"Revisa la información e intenta de nuevo.");
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No fue posible crear tus datos");
		}
	}

	public void updateCustomer(String documentId, CustomerCreateRequest request) {
		try {
			String result = softixClient.updateCustomer(documentId, request);
			if (result == null || result.isBlank()) {
				throw new ApiException(HttpStatus.BAD_GATEWAY,
						"No pudimos guardar tus datos. Intenta de nuevo.");
			}
		} catch (SoftixClientException ex) {
			if (isCustomerNotFound(ex)) {
				throw new ApiException(HttpStatus.NOT_FOUND,
						"No encontramos tus datos. Contáctanos.");
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No fue posible actualizar tus datos");
		}
	}

	public boolean isDocumentAvailable(String documentId) {
		try {
			return softixClient.findCustomer(documentId) == null;
		} catch (SoftixClientException ex) {
			if (isCustomerNotFound(ex)) {
				return true;
			}
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No fue posible validar el documento");
		}
	}

	public List<DocumentTypeResponse> documentTypes() {
		try {
			List<DocumentTypeItem> items = softixClient.listDocumentTypes();
			if (items == null) {
				return List.of();
			}

			Map<String, DocumentTypeResponse> accepted = new LinkedHashMap<>();
			for (DocumentTypeItem item : items) {
				String id = item.documentTypeId() == null ? ""
						: item.documentTypeId().trim().toUpperCase(Locale.ROOT);
				if (!CUSTOMER_DOCUMENT_TYPES.contains(id) || accepted.containsKey(id)) {
					continue;
				}
				accepted.put(id, new DocumentTypeResponse(id,
						CUSTOMER_DOCUMENT_TYPE_NAMES.getOrDefault(id, item.documentName()),
						item.code()));
			}

			return CUSTOMER_DOCUMENT_TYPES.stream()
					.map(accepted::get)
					.filter(java.util.Objects::nonNull)
					.toList();
		} catch (SoftixClientException ex) {
			throw new ApiException(HttpStatus.BAD_GATEWAY,
					"No fue posible consultar los tipos de documento");
		}
	}

	public CustomerCreateRequest buildCreateRequest(SoftixCreateCustomerRequest request,
			String phoneE164) {
		return CustomerCreateRequest.builder()
				.documentId(request.documentId())
				.documentType(request.documentType())
				.firstName(request.firstName())
				.middleName(request.middleName())
				.lastName(request.lastName())
				.secondLastName(request.secondLastName())
				.mobile(phoneNormalizer.nationalDigits(phoneE164))
				.email(emailOrPlaceholder(request.email(), phoneE164))
				.address("")
				.birthDate(atMidnight(request.birthDate()))
				.gender(request.gender())
				.city(DEFAULT_CITY)
				.locality(DEFAULT_LOCALITY)
				.postalCode("")
				.maritalStatus("")
				.occupation("")
				.authorization(SOFTIX_AUTHORIZATION_GRANTED)
				.bloodType(SOFTIX_BLOOD_TYPE_UNKNOWN)
				.taxResponsibility(SOFTIX_NO_TAX_RESPONSIBILITY)
				.taxes(SOFTIX_NO_TAX_RESPONSIBILITY)
				.regime(SOFTIX_REGIME)
				.priceList(SOFTIX_PRICE_LIST)
				.country(SOFTIX_COUNTRY)
				.personType(SOFTIX_PERSON_TYPE)
				.creationBranch(creationBranch)
				.createdBy(createdBy)
				.creationDate(nowIso())
				.build();
	}

	public CustomerCreateRequest merge(CustomerResponse current,
			SoftixProfileUpdateRequest request, String phoneE164, boolean authorized) {
		return merge(current, request, phoneE164, authorized, current.mobile());
	}

	/**
	 * Vincula una ficha existente al teléfono verificado. Mantiene los datos que
	 * no se editan en el autoservicio y reemplaza solo la lista blanca del
	 * formulario.
	 */
	public CustomerCreateRequest mergeForLink(CustomerResponse current,
			SoftixCreateCustomerRequest request, String phoneE164) {
		SoftixProfileUpdateRequest update = new SoftixProfileUpdateRequest(
				request.firstName(),
				request.middleName(),
				request.lastName(),
				request.secondLastName(),
				request.email(),
				request.birthDate(),
				request.gender());
		return merge(current, update, phoneE164, true,
				phoneNormalizer.nationalDigits(phoneE164));
	}

	/**
	 * Indica si la ficha puede vincularse al teléfono verificado. Un teléfono
	 * fijo o un valor no normalizable no bloquea la vinculación; un celular
	 * distinto sí.
	 */
	public boolean phoneMatches(CustomerResponse customer, String phoneE164) {
		String national = phoneNormalizer.nationalDigits(phoneE164);
		return phoneIsEmptyOrMatches(customer.mobile(), national)
				&& phoneIsEmptyOrMatches(customer.phone(), national);
	}

	public boolean isSupportedDocumentType(String documentType) {
		if (documentType == null || documentType.isBlank()) {
			return false;
		}
		return CUSTOMER_DOCUMENT_TYPES.contains(documentType.trim().toUpperCase(Locale.ROOT));
	}

	private boolean phoneIsEmptyOrMatches(String value, String national) {
		if (value == null || value.isBlank()) {
			return true;
		}
		try {
			return phoneNormalizer.normalize(value).equals("+57" + national);
		} catch (RuntimeException ex) {
			return true;
		}
	}

	private CustomerCreateRequest merge(CustomerResponse current,
			SoftixProfileUpdateRequest request, String phoneE164, boolean authorized,
			String mobile) {
		return CustomerCreateRequest.builder()
				.codeClient("")
				.documentId(current.documentId())
				.documentType(current.documentType())
				.firstName(requiredOrCurrent(request.firstName(), current.firstName()))
				.middleName(textOrCurrent(request.middleName(), current.middleName()))
				.lastName(requiredOrCurrent(request.lastName(), current.lastName()))
				.secondLastName(textOrCurrent(request.secondLastName(), current.secondLastName()))
				.checkDigit(current.checkDigit())
				.mobile(mobile)
				.phone(current.phone())
				.company(current.company())
				.occupation(current.occupation())
				.maritalStatus(current.maritalStatus())
				.email(emailOrPlaceholder(request.email(), phoneE164))
				.address(current.address())
				.notes(current.notes())
				.authorization(authorized ? SOFTIX_AUTHORIZATION_GRANTED
						: intOrDefault(current.authorization(), 0))
				.birthDate(request.birthDate() == null
						? formatCurrentBirthDate(current.birthDate())
						: atMidnight(request.birthDate()))
				.membership(current.membership())
				.healthProvider(current.healthProvider())
				.city(requiredOrCurrent(current.city(), DEFAULT_CITY))
				.locality(requiredOrCurrent(current.locality(), DEFAULT_LOCALITY))
				.gender(requiredOrCurrent(request.gender(), current.gender()))
				.rural(intOrDefault(current.rural(), 0))
				.consultationValue(doubleOrDefault(current.consultationValue(), 0))
				.personType(textOrDefault(current.personType(), SOFTIX_PERSON_TYPE))
				.taxResponsibility(textOrDefault(current.taxResponsibility(), SOFTIX_NO_TAX_RESPONSIBILITY))
				.taxes(textOrDefault(current.taxes(), SOFTIX_NO_TAX_RESPONSIBILITY))
				.postalCode(current.postalCode())
				.loyaltyNotes(current.loyaltyObservation())
				.regime(textOrDefault(current.regime(), SOFTIX_REGIME))
				.country(textOrDefault(current.country(), SOFTIX_COUNTRY))
				.priceList(textOrDefault(current.priceList(), SOFTIX_PRICE_LIST))
				.blockedSales(Boolean.TRUE.equals(current.blockedSales()))
				.maxCreditLimit(doubleOrDefault(current.maxCreditLimit(), 0))
				.creditDays(intOrDefault(current.creditDays(), 0))
				.creationBranch(textOrDefault(current.creationBranch(), creationBranch))
				.createdBy(textOrDefault(current.createdBy(), createdBy))
				.bloodType(textOrDefault(current.bloodType(), SOFTIX_BLOOD_TYPE_UNKNOWN))
				.socialNetwork(current.socialNetwork())
				.codAffinityHoya(intOrDefault(current.codAffinityHoya(), 0))
				.creationDate(current.creationDate() == null
						? nowIso()
						: current.creationDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
				.additionalDiscount(doubleOrDefault(current.additionalDiscount(), 0))
				.build();
	}

	public SoftixProfileResponse toProfile(CustomerResponse customer) {
		return new SoftixProfileResponse(
				customer.documentType(),
				maskDocument(customer.documentId()),
				customer.firstName(),
				customer.middleName(),
				customer.lastName(),
				customer.secondLastName(),
				customer.email(),
				customer.birthDate() == null ? null : customer.birthDate().toLocalDate(),
				customer.gender(),
				customer.city(),
				customer.locality(),
				customer.postalCode(),
				customer.maritalStatus(),
				customer.occupation());
	}

	public SoftixProfileResponse toProfile(CustomerCreateRequest request) {
		return new SoftixProfileResponse(
				request.documentType(),
				maskDocument(request.documentId()),
				request.firstName(),
				request.middleName(),
				request.lastName(),
				request.secondLastName(),
				request.email(),
				request.birthDate() == null ? null
						: LocalDateTime.parse(request.birthDate()).toLocalDate(),
				request.gender(),
				request.city(),
				request.locality(),
				request.postalCode(),
				request.maritalStatus(),
				request.occupation());
	}

	public String findMatch(List<String> candidates, String input) {
		if (input == null || input.isBlank()) {
			return null;
		}
		String value = DocumentNumbers.baseDigits(input);
		if (value.isEmpty()) {
			return null;
		}
		List<String> matches = candidates.stream()
				.filter(candidate -> DocumentNumbers.baseDigits(candidate).equals(value))
				.toList();
		if (matches.size() == 1) {
			return matches.get(0);
		}
		if (matches.size() > 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST,
					"Hay varios registros que coinciden. Contáctanos.");
		}
		return null;
	}

	public void upsertIndex(String phoneE164, String clientId) {
		boolean exists = indexRepository.findByPhoneE164OrderBySoftixClientId(phoneE164).stream()
				.anyMatch(row -> row.getSoftixClientId().equals(clientId));
		if (exists) {
			return;
		}
		SoftixCustomerIndex row = new SoftixCustomerIndex();
		row.setPhoneE164(phoneE164);
		row.setSoftixClientId(clientId);
		row.setSyncedAt(LocalDateTime.now());
		indexRepository.save(row);
	}

	public boolean hasIndexForPhone(String phoneE164) {
		return indexRepository.countByPhoneE164(phoneE164) > 0;
	}

	public String maskDocument(String documentId) {
		return DocumentNumbers.mask(documentId);
	}

	private String emailOrPlaceholder(String email, String phoneE164) {
		if (email != null && !email.isBlank()) {
			return email.trim();
		}
		return phoneNormalizer.placeholderEmail(phoneE164, placeholderEmailDomain);
	}

	private String atMidnight(java.time.LocalDate date) {
		return date == null ? null : date.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	private String formatCurrentBirthDate(LocalDateTime date) {
		return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	private String nowIso() {
		return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
				.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
	}

	private String requiredOrCurrent(String value, String current) {
		if (value == null) {
			return current;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? current : trimmed;
	}

	private String textOrCurrent(String value, String current) {
		if (value == null) {
			return current;
		}
		return value.trim();
	}

	/**
	 * Para campos de sistema que Softix exige. Si la ficha vieja los trae
	 * vacíos, se envía el valor por defecto en lugar de un string vacío.
	 */
	private String textOrDefault(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private int intOrDefault(Integer value, int fallback) {
		return value == null ? fallback : value;
	}

	private double doubleOrDefault(BigDecimal value, double fallback) {
		return value == null ? fallback : value.doubleValue();
	}

	private boolean isCustomerNotFound(SoftixClientException ex) {
		if (ex.getStatusCode() == null
				|| (ex.getStatusCode() != 400 && ex.getStatusCode() != 404)) {
			return false;
		}
		String body = ex.getResponseBody();
		if (body == null) {
			return false;
		}
		String normalized = body.toLowerCase();
		return normalized.contains("not registered")
				|| normalized.contains("not found")
				|| normalized.contains("no está registrado")
				|| normalized.contains("no esta registrado");
	}
}
