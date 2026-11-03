package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

                                public record CustomerCreateRequest(
                                                                                                String documentId,
                                                                                                String documentType,
                                                                                                String firstName,
                                                                                                String middleName,
                                                                                                String lastName,
                                                                                                String secondLastName,
                                                                                                String mobile,
                                                                                                String phone,
                                                                                                String email,
                                                                                                String birthDate,
                                                                                                String city,
                                                                                                String locality,
                                                                                                String address,
                                                                                                String gender,
                                                                                                String country,
                                                                                                String personType,
                                                                                                String creationBranch,
                                                                                                String createdBy) {
                                }

                                public record InventoryPage(List<InventoryItem> glassesFrames) {
                                }

                                public record InventoryItem(String code, String description, String productType,
                                                                                                Integer quantity,
                                                                                                BigDecimal price,
                                                                                                BigDecimal price2,
                                                                                                BigDecimal maxDiscount) {
                                }

                                public record SchedulePage(List<AvailableTime> listAvailableTimes) {
                                }

                                public record AvailableTime(String id, String optometrist, String time) {
                                }
}
