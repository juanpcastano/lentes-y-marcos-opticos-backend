package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

import static com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.*;

@Component
@Slf4j
public class SoftixClient {
    private static final Duration TOKEN_SAFETY_MARGIN = Duration.ofSeconds(20);

    private final RestClient restClient;
    private final String email;
    private final String password;
    private final Object tokenLock = new Object();
    private volatile CachedToken cachedToken;

    public SoftixClient(RestClient softixRestClient,
            @Value("${softix.email}") String email,
            @Value("${softix.password}") String password) {
        this.restClient = softixRestClient;
        this.email = email;
        this.password = password;
    }

    public CustomerResponse findCustomer(String documentId) {
        return executeAuthorized(token -> restClient.get()
                .uri("/api/customers/{documentId}", documentId)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(CustomerEnvelope.class)).customer();
    }

    public String createCustomer(CustomerCreateRequest request) {
        return executeAuthorized(token -> restClient.post()
                .uri("/api/customers/create")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class));
    }

    public InventoryPage listFrames(String branchId, int page, int pageSize) {
        return executeAuthorized(token -> restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/inventories/frames/list/{branchId}")
                        .queryParam("page", page).queryParam("pageSize", pageSize)
                        .build(branchId))
                .header("Authorization", "Bearer " + token)
                .retrieve().body(InventoryPage.class));
    }

    public SchedulePage listSchedules(String branchId, LocalDate date) {
        return executeAuthorized(token -> restClient.get()
                .uri("/api/schedules/{branchId}/{date}", branchId, date)
                .header("Authorization", "Bearer " + token)
                .retrieve().body(SchedulePage.class));
    }

    private <T> T executeAuthorized(java.util.function.Function<String, T> request) {
        try {
            return request.apply(getToken());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                invalidateToken();
                return request.apply(getToken());
            }
            throw externalError(ex);
        } catch (RuntimeException ex) {
            throw externalError(ex);
        }
    }

    private String getToken() {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plus(TOKEN_SAFETY_MARGIN))) {
            return current.value();
        }
        synchronized (tokenLock) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(Instant.now().plus(TOKEN_SAFETY_MARGIN))) {
                return current.value();
            }
            if (email.isBlank() || password.isBlank()) {
                throw new SoftixClientException("Softix no está configurado");
            }
            try {
                LoginResponse response = restClient
                        .post()
                        .uri("/api/account/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new LoginRequest(email, password))
                        .retrieve()
                        .body(LoginResponse.class);
                if (response == null || response.token() == null || response.token().isBlank()) {
                    throw new SoftixClientException("Softix no devolvió un token");
                }
                Instant expiration = parseExpiration(response.expiration());
                cachedToken = new CachedToken(response.token(), expiration);
                return response.token();
            } catch (RuntimeException ex) {
                throw externalError(ex);
            }
        }
    }

    private void invalidateToken() {
        cachedToken = null;
    }

    private Instant parseExpiration(String expiration) {
        if (expiration == null || expiration.isBlank()) {
            return Instant.now().plus(Duration.ofMinutes(5));
        }
        try {
            return Instant.parse(expiration);
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return java.time.LocalDateTime.parse(expiration)
                        .toInstant(java.time.ZoneOffset.UTC);
            } catch (java.time.format.DateTimeParseException invalid) {
                log.warn("Softix devolvió una expiración de token no reconocida");
                return Instant.now().plus(Duration.ofMinutes(4));
            }
        }
    }

    private SoftixClientException externalError(Throwable ex) {
        if (ex instanceof SoftixClientException softixException)
            return softixException;
        if (ex instanceof RestClientResponseException responseException) {
            log.warn("Error comunicando con Softix: {}", responseException.getStatusCode());
            return new SoftixClientException(
                    "Softix devolvió un error HTTP",
                    responseException.getStatusCode().value(),
                    responseException.getResponseBodyAsString());
        }
        log.warn("Error comunicando con Softix: {}", ex.getMessage());
        return new SoftixClientException("No fue posible comunicarse con Softix", ex);
    }

    private record CachedToken(String value, Instant expiresAt) {
        private CachedToken {
            Objects.requireNonNull(value);
            Objects.requireNonNull(expiresAt);
        }
    }
}
