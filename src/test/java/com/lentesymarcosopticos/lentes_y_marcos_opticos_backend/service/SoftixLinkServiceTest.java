package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixLinkResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.SoftixCreateLinkRequest;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.entity.User;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.CustomerResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SoftixLinkServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private SoftixClient softixClient;

    private SoftixLinkService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SoftixLinkService(userRepository, softixClient);
        user = new User();
        user.setEmail("user@test.com");
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@test.com", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void vinculaClienteEncontradoEnSoftix() {
        when(softixClient.findCustomer("123")).thenReturn(customer());

        SoftixLinkResponse response = service.link(new SoftixLinkRequest("CC", "123"));

        assertThat(response.clientId()).isEqualTo("123");
        assertThat(response.firstName()).isEqualTo("Ana");
        assertThat(user.getSoftixClientId()).isEqualTo("123");
        assertThat(user.getSoftixDocumentType()).isEqualTo("CC");
        assertThat(user.getSoftixDocumentId()).isEqualTo("123");
        verify(userRepository).save(user);
    }

    @Test
    void noConsultaSoftixSiLaCuentaYaEstaVinculada() {
        user.setSoftixClientId("123");
        user.setSoftixDocumentType("CC");
        user.setSoftixDocumentId("123");

        SoftixLinkResponse response = service.link(new SoftixLinkRequest("CC", "123"));

        assertThat(response.clientId()).isEqualTo("123");
        verify(softixClient, never()).findCustomer(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void rechazaIntentoDeVincularOtroCliente() {
        user.setSoftixClientId("123");
        user.setSoftixDocumentType("CC");
        user.setSoftixDocumentId("123");

        assertThatThrownBy(() -> service.link(new SoftixLinkRequest("CC", "456")))
                .isInstanceOf(ApiException.class)
                .hasMessage("La cuenta ya está vinculada a otro cliente Softix");

        verify(softixClient, never()).findCustomer(any());
    }

    @Test
    void creaClienteEnSoftixYLoVincula() {
        when(softixClient.createCustomer(any())).thenReturn("Client created");

        SoftixLinkResponse response = service.createAndLink(new SoftixCreateLinkRequest(
                "CC", "456", "Luis", null, "Gómez", null,
                "3009876543", "luis@test.com", LocalDate.of(1990, 1, 2),
                "11", "001", "Calle 1"));

        assertThat(response.clientId()).isEqualTo("456");
        assertThat(user.getSoftixClientId()).isEqualTo("456");
        verify(softixClient).createCustomer(any());
        verify(userRepository).save(user);
    }

    private CustomerResponse customer() {
        return new CustomerResponse(
                "123", "CC", "Ana", null, "Pérez", null,
                "3001234567", null, "ana@test.com", null,
                null, null, null, null);
    }
}
