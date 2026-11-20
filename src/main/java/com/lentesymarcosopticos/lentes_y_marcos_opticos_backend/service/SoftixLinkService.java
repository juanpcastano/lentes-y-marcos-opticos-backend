package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

@Service
public class SoftixLinkService {
    // Valores de catálogo observados en clientes reales de Softix (muestra de 1000 clientes).
    private static final String SOFTIX_REGIME = "49";
    private static final String SOFTIX_PRICE_LIST = "1";
    private static final String SOFTIX_COUNTRY = "CO";
    private static final String SOFTIX_PERSON_TYPE = "Natural";
    private static final String SOFTIX_NO_TAX_RESPONSIBILITY = "ZZ";
    // Autorización de tratamiento de datos. Pendiente de confirmar el flujo de consentimiento.
    private static final int SOFTIX_AUTHORIZATION_GRANTED = 1;
    // Softix confirmó que "NE" (No especificado) es el valor para cuando el cliente no quiere indicar su tipo de sangre.
    private static final String SOFTIX_BLOOD_TYPE_UNKNOWN = "NE";

    private final UserRepository userRepository;
    private final SoftixClient softixClient;
    private final String creationBranch;
    private final String createdBy;

    public SoftixLinkService(UserRepository userRepository,
            SoftixClient softixClient,
            @Value("${softix.creation-branch}") String creationBranch,
            @Value("${softix.created-by}") String createdBy) {
        this.userRepository = userRepository;
        this.softixClient = softixClient;
        this.creationBranch = creationBranch;
        this.createdBy = createdBy;
    }

    @Transactional
    public SoftixLinkResponse link(SoftixLinkRequest request) {
        User user = currentUser();

        if (user.getSoftixClientId() != null) {
            if (user.getSoftixDocumentType() == null
                    || !user.getSoftixDocumentType().equalsIgnoreCase(request.documentType())
                    || !user.getSoftixDocumentId().equals(request.documentId())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "La cuenta ya está vinculada a otro cliente Softix");
            }
            return new SoftixLinkResponse(
                    user.getSoftixClientId(), user.getSoftixDocumentType(),
                    user.getSoftixDocumentId(), null, null, null, null);
        }

        CustomerResponse customer;
        try {
            customer = softixClient.findCustomer(request.documentId());
        } catch (SoftixClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No fue posible validar el cliente en Softix");
        }
        if (customer == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Softix no devolvió información del cliente");
        }

        user.setSoftixClientId(customer.documentId());
        user.setSoftixDocumentType(request.documentType());
        user.setSoftixDocumentId(request.documentId());
        userRepository.save(user);

        return toResponse(customer);
    }

    @Transactional
    public SoftixLinkResponse createAndLink(SoftixCreateLinkRequest request) {
        User user = currentUser();
        rejectIfAlreadyLinked(user, request.documentType(), request.documentId());

        try {
            String creationResponse = softixClient.createCustomer(CustomerCreateRequest.builder()
                    .documentId(request.documentId())
                    .documentType(request.documentType())
                    .firstName(request.firstName())
                    .middleName(request.middleName())
                    .lastName(request.lastName())
                    .secondLastName(request.secondLastName())
                    .mobile(request.mobile())
                    .email(request.email())
                    .birthDate(request.birthDate() + "T00:00:00")
                    .address(request.address())
                    .city(request.city())
                    .locality(request.locality())
                    .gender(request.gender())
                    .bloodType(SOFTIX_BLOOD_TYPE_UNKNOWN)
                    .authorization(SOFTIX_AUTHORIZATION_GRANTED)
                    .taxResponsibility(SOFTIX_NO_TAX_RESPONSIBILITY)
                    .taxes(SOFTIX_NO_TAX_RESPONSIBILITY)
                    .regime(SOFTIX_REGIME)
                    .priceList(SOFTIX_PRICE_LIST)
                    .country(SOFTIX_COUNTRY)
                    .personType(SOFTIX_PERSON_TYPE)
                    .creationBranch(creationBranch)
                    .createdBy(createdBy)
                    .creationDate(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .build());
            if (creationResponse == null || creationResponse.isBlank()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "Softix no confirmó la creación del cliente");
            }
        } catch (SoftixClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No fue posible crear el cliente en Softix");
        }

        user.setSoftixClientId(request.documentId());
        user.setSoftixDocumentType(request.documentType());
        user.setSoftixDocumentId(request.documentId());
        userRepository.save(user);

        return new SoftixLinkResponse(
                request.documentId(), request.documentType(), request.documentId(),
                request.firstName(), joinNames(request.lastName(), request.secondLastName()),
                request.email(), request.mobile());
    }

    private void rejectIfAlreadyLinked(User user, String documentType, String documentId) {
        if (user.getSoftixClientId() != null) {
            if (user.getSoftixDocumentType() == null
                    || !user.getSoftixDocumentType().equalsIgnoreCase(documentType)
                    || !user.getSoftixDocumentId().equals(documentId)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "La cuenta ya está vinculada a otro cliente Softix");
            }
            throw new ApiException(HttpStatus.CONFLICT,
                    "La cuenta ya está vinculada a este cliente Softix");
        }
    }

    private User currentUser() {
        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private SoftixLinkResponse toResponse(CustomerResponse customer) {
        return new SoftixLinkResponse(
                customer.documentId(),
                customer.documentType(),
                customer.documentId(),
                customer.firstName(),
                joinNames(customer.lastName1(), customer.lastName2()),
                customer.email(),
                customer.mobile());
    }

    private String joinNames(String first, String second) {
        if (first == null) return second;
        if (second == null || second.isBlank()) return first;
        return first + " " + second;
    }
}
