package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;

public final class SoftixDtos {
    private SoftixDtos() {
    }

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String token, String expiration) {
    }

    public record CustomerResponse(
            String documentId,
            String documentType,
            String firstName,
            String middleName,
            String lastName1,
            String lastName2,
            String mobile,
            String phone,
            String email,
            String address,
            LocalDateTime birthDate,
            String city,
            String district,
            String gender) {
    }

    public record CustomerEnvelope(String message, CustomerResponse customer) {
    }

    /**
     * Body de POST /api/Customers/Create. Los nombres de los campos deben coincidir
     * con el esquema "Client" de https://api.softix.app/swagger/v1/swagger.json.
     * Los campos primitivos (int, double, boolean) se envían siempre, aunque no se
     * asignen: toman 0 o false.
     */
    @Builder
    public record CustomerCreateRequest(
            String codeClient,
            String documentId,
            String documentType,
            String firstName,
            String middleName,
            String lastName,
            String secondLastName,
            String checkDigit,
            String mobile,
            String phone,
            String company,
            String occupation,
            String maritalStatus,
            String email,
            String address,
            String notes,
            int authorization,
            String birthDate,
            String membership,
            String healthProvider,
            String city,
            String locality,
            String gender,
            int rural,
            double consultationValue,
            String personType,
            String taxResponsibility,
            String taxes,
            String postalCode,
            String loyaltyNotes,
            String regime,
            String country,
            String priceList,
            boolean blockedSales,
            double maxCreditLimit,
            int creditDays,
            String creationBranch,
            String createdBy,
            String bloodType,
            String socialNetwork,
            String cobertura,
            int codAffinityHoya,
            String creationDate,
            double additionalDiscount) {

        public CustomerCreateRequest {
            codeClient = emptyIfNull(codeClient);
            middleName = emptyIfNull(middleName);
            secondLastName = emptyIfNull(secondLastName);
            checkDigit = emptyIfNull(checkDigit);
            phone = emptyIfNull(phone);
            company = emptyIfNull(company);
            occupation = emptyIfNull(occupation);
            maritalStatus = emptyIfNull(maritalStatus);
            notes = emptyIfNull(notes);
            membership = emptyIfNull(membership);
            healthProvider = emptyIfNull(healthProvider);
            postalCode = emptyIfNull(postalCode);
            loyaltyNotes = emptyIfNull(loyaltyNotes);
            socialNetwork = emptyIfNull(socialNetwork);
            cobertura = emptyIfNull(cobertura);
            address = emptyIfNull(address);
        }

        private static String emptyIfNull(String value) {
            return value == null ? "" : value;
        }
    }

    /** Respuesta de GET /api/Inventories/available/{sku}. */
    public record AvailabilityEnvelope(AvailabilityData data) {
    }

    public record AvailabilityData(String productCode, Integer availableQuantity) {
    }

    /** Fila de GET /api/Inventarios/monturas/lista/{sede}. El valor es el precio unitario. */
    public record StockItem(String codigo, String descripcion, String tipoProducto,
            Integer cantidad,
            BigDecimal valor,
            BigDecimal valor2,
            BigDecimal maxDescuento) {
    }

    /** Respuesta de GET /api/Inventories/products/list/{productType}. */
    public record ProductPage(List<ProductItem> products, Integer totalPages, Integer totalRecords) {
    }

    /**
     * Solo los campos que usa el catálogo. salePrice es el precio unitario de venta;
     * saleVAT = 1 significa que ese precio ya incluye IVA. Es informativo: no altera el precio.
     */
    public record ProductItem(String code, String name, String brand, String productType,
            String color, String reference, String category, BigDecimal salePrice,
            @JsonProperty("saleVAT") Integer saleVat, LocalDateTime creationDate) {
    }

    public record SchedulePage(List<AvailableTime> listAvailableTimes) {
    }

    public record AvailableTime(String id, String optometrist, String time) {
    }
}
