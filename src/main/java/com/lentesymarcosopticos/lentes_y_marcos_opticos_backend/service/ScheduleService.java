package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.dto.AvailableScheduleResponse;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.exception.ApiException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClientException;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.AvailableTime;

@Service
public class ScheduleService {
    private final SoftixClient softixClient;
    private final List<String> branchIds;

    public ScheduleService(SoftixClient softixClient,
            @Value("${softix.branch-ids}") String configuredBranchIds) {
        this.softixClient = softixClient;
        this.branchIds = Arrays.stream(configuredBranchIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    public List<AvailableScheduleResponse> listAvailable(LocalDate date) {
        if (date == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha es obligatoria");
        }
        if (branchIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No hay sedes Softix configuradas para consultar horarios");
        }

        Map<String, AvailableScheduleResponse> uniqueSlots = new LinkedHashMap<>();
        try {
            for (String branchId : branchIds) {
                List<AvailableTime> slots;
                try {
                    slots = softixClient.listSchedules(branchId, date).listAvailableTimes();
                } catch (SoftixClientException ex) {
                    if (isScheduleNotConfigured(ex)) {
                        continue;
                    }
                    throw ex;
                }
                if (slots == null) {
                    slots = Collections.emptyList();
                }
                for (AvailableTime slot : slots) {
                    AvailableScheduleResponse response = new AvailableScheduleResponse(
                            slot.id(), slot.optometrist(), slot.time());
                    uniqueSlots.putIfAbsent(slotKey(response), response);
                }
            }
            return List.copyOf(uniqueSlots.values());
        } catch (SoftixClientException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "No fue posible consultar los horarios en Softix");
        }
    }

    private boolean isScheduleNotConfigured(SoftixClientException ex) {
        return Integer.valueOf(400).equals(ex.getStatusCode())
                && ex.getResponseBody() != null
                && ex.getResponseBody().toLowerCase(Locale.ROOT)
                        .contains("no tiene configurado horarios web");
    }

    private String slotKey(AvailableScheduleResponse slot) {
        return String.join("|", value(slot.id()), value(slot.optometrist()), value(slot.time()));
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
