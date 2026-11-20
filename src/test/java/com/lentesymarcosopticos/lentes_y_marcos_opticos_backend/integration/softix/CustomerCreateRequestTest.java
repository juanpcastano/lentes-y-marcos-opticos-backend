package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerCreateRequest;

class CustomerCreateRequestTest {

    // Campos "required" del esquema Client en https://api.softix.app/swagger/v1/swagger.json
    private static final List<String> SOFTIX_REQUIRED_FIELDS = Arrays.asList(
            "authorization", "birthDate", "blockedSales", "bloodType", "city", "consultationValue",
            "country", "createdBy", "creationBranch", "creditDays", "documentId", "documentType",
            "email", "firstName", "gender", "locality", "maxCreditLimit", "personType", "priceList",
            "regime", "rural", "taxes", "taxResponsibility");

    @Test
    void incluyeTodosLosCamposObligatoriosDeSoftix() {
        Set<String> dtoFields = Arrays.stream(CustomerCreateRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(dtoFields).containsAll(SOFTIX_REQUIRED_FIELDS);
    }

    @Test
    void convierteNulosOpcionalesEnCadenaVacia() {
        CustomerCreateRequest request = CustomerCreateRequest.builder()
                .documentId("1000000100")
                .build();

        assertThat(request.middleName()).isEmpty();
        assertThat(request.secondLastName()).isEmpty();
        assertThat(request.phone()).isEmpty();
        assertThat(request.socialNetwork()).isEmpty();
        assertThat(request.documentId()).isEqualTo("1000000100");
    }
}
