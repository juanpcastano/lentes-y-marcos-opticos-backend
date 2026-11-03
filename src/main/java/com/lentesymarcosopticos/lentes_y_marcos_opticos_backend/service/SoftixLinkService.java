package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

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
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class SoftixLinkService {
    private final UserRepository userRepository;
    private final SoftixClient softixClient;

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
            String creationResponse = softixClient.createCustomer(new com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest(
                    request.documentId(), request.documentType(), request.firstName(), request.middleName(),
                    request.lastName(), request.secondLastName(), request.mobile(), null, request.email(),
                    request.birthDate() + "T00:00:00", request.city(), request.locality(), request.address(),
                    "No especificado", "CO", "Natural", null, null));
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
