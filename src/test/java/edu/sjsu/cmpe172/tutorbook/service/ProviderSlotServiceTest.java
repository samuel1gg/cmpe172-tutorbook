package edu.sjsu.cmpe172.tutorbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.dto.form.SlotForm;
import edu.sjsu.cmpe172.tutorbook.exception.BadRequestException;
import edu.sjsu.cmpe172.tutorbook.exception.ForbiddenException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.ServiceOffering;
import edu.sjsu.cmpe172.tutorbook.model.SlotState;
import edu.sjsu.cmpe172.tutorbook.repository.ServiceOfferingRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Unit tests for provider slot rules. */
class ProviderSlotServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    static final long PROVIDER = 2;

    SlotRepository slots = mock(SlotRepository.class);
    ServiceOfferingRepository services = mock(ServiceOfferingRepository.class);
    ProviderSlotService service = new ProviderSlotService(slots, services,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));

    static SlotForm form(LocalDate date, LocalTime time) {
        SlotForm f = new SlotForm();
        f.setServiceId(2L);
        f.setDate(date);
        f.setStartTime(time);
        return f;
    }

    @Test
    void createsSlotWithEndTimeFromServiceDuration() {
        when(services.findById(2)).thenReturn(Optional.of(new ServiceOffering(2L, "Java", "", 45, 4500)));
        LocalDateTime start = LocalDateTime.of(2026, 10, 9, 10, 0);
        when(slots.insert(PROVIDER, 2, start, start.plusMinutes(45))).thenReturn(200L);

        assertThat(service.createSlot(PROVIDER, form(LocalDate.of(2026, 10, 9), LocalTime.of(10, 0)))).isEqualTo(200L);
    }

    @Test
    void startInThePastIsBadRequest() {
        when(services.findById(2)).thenReturn(Optional.of(new ServiceOffering(2L, "Java", "", 60, 4500)));

        assertThatThrownBy(() -> service.createSlot(PROVIDER, form(LocalDate.of(2026, 10, 8), LocalTime.of(9, 0))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void overlappingSlotIsConflict() {
        when(services.findById(2)).thenReturn(Optional.of(new ServiceOffering(2L, "Java", "", 60, 4500)));
        when(slots.overlapExists(PROVIDER, LocalDateTime.of(2026, 10, 9, 10, 0), LocalDateTime.of(2026, 10, 9, 11, 0)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createSlot(PROVIDER, form(LocalDate.of(2026, 10, 9), LocalTime.of(10, 0))))
                .isInstanceOf(SlotConflictException.class);
    }

    @Test
    void cannotRemoveAnotherProvidersSlot() {
        when(slots.findState(9)).thenReturn(Optional.of(new SlotState(9, 99, NOW.plusDays(1), false, false, 0)));

        assertThatThrownBy(() -> service.removeSlot(PROVIDER, 9)).isInstanceOf(ForbiddenException.class);
        verify(slots, never()).markRemoved(anyLong());
    }

    @Test
    void cannotRemoveABookedSlot() {
        when(slots.findState(9)).thenReturn(Optional.of(new SlotState(9, PROVIDER, NOW.plusDays(1), false, true, 0)));

        assertThatThrownBy(() -> service.removeSlot(PROVIDER, 9)).isInstanceOf(SlotConflictException.class);
        verify(slots, never()).markRemoved(anyLong());
    }

    @Test
    void removesOwnOpenSlot() {
        when(slots.findState(9)).thenReturn(Optional.of(new SlotState(9, PROVIDER, NOW.plusDays(1), false, false, 3)));
        when(slots.bumpVersion(9, 3)).thenReturn(true);

        service.removeSlot(PROVIDER, 9);

        verify(slots).markRemoved(9);
    }
}
