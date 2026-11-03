package com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixClient;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.AvailableTime;
import com.lentesymarcosopticos.lentes_y_marcos_opticos_backend.integration.softix.SoftixDtos.SchedulePage;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
    @Mock
    private SoftixClient softixClient;

    @Test
    void fusionaHorariosDeLasDosSedesSinDuplicarlos() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        when(softixClient.listSchedules("1", date)).thenReturn(new SchedulePage(List.of(
                new AvailableTime("slot-1", "Ana", "09:00:00"),
                new AvailableTime("slot-2", "Ana", "10:00:00"))));
        when(softixClient.listSchedules("2", date)).thenReturn(new SchedulePage(List.of(
                new AvailableTime("slot-1", "Ana", "09:00:00"),
                new AvailableTime("slot-3", "Luis", "11:00:00"))));

        ScheduleService service = new ScheduleService(softixClient, "1,2");

        var schedules = service.listAvailable(date);

        assertThat(schedules).hasSize(3);
        assertThat(schedules).extracting(schedule -> schedule.time())
                .containsExactly("09:00:00", "10:00:00", "11:00:00");
    }
}
