package edu.sjsu.cmpe172.tutorbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.dto.AppointmentListDto;
import edu.sjsu.cmpe172.tutorbook.exception.ForbiddenException;
import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/** Unit tests for owner-only cancel and the upcoming/history split. */
class AppointmentServiceTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    static final AppUser ALICE = new AppUser(1, "alice@x", "hash", "Alice", "CUSTOMER", null);
    static final AppUser BOB = new AppUser(2, "bob@x", "hash", "Bob", "CUSTOMER", null);
    static final AppUser TUTOR = new AppUser(5, "kim@x", "hash", "Kim", "PROVIDER", 2L);

    AppointmentRepository repo = mock(AppointmentRepository.class);
    AppointmentService service = new AppointmentService(repo,
            Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));

    static AppointmentDetails appt(long id, long customerId, String status, LocalDateTime start) {
        return new AppointmentDetails(id, status, null, NOW.minusDays(5), customerId, "Name", "e@x",
                10L, 2L, start, start.plusHours(1), "Tutor", "Java Help", 4500);
    }

    @Test
    void ownerCanCancelUpcomingBooking() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "BOOKED", NOW.plusDays(1))));
        when(repo.cancel(3)).thenReturn(1);

        service.cancel(ALICE, 3);

        verify(repo).cancel(3);
    }

    @Test
    void someoneElsesAppointmentIsForbidden() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "BOOKED", NOW.plusDays(1))));

        assertThatThrownBy(() -> service.cancel(BOB, 3)).isInstanceOf(ForbiddenException.class);
        verify(repo, never()).cancel(anyLong());
    }

    @Test
    void cancellingTwiceIsAConflict() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "CANCELLED", NOW.plusDays(1))));

        assertThatThrownBy(() -> service.cancel(ALICE, 3)).isInstanceOf(SlotConflictException.class);
    }

    @Test
    void startedAppointmentCannotBeCancelled() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "BOOKED", NOW.minusMinutes(5))));

        assertThatThrownBy(() -> service.cancel(ALICE, 3)).isInstanceOf(SlotConflictException.class);
    }

    @Test
    void lostCancelRaceIsAConflict() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "BOOKED", NOW.plusDays(1))));
        when(repo.cancel(3)).thenReturn(0); // another request cancelled it first

        assertThatThrownBy(() -> service.cancel(ALICE, 3)).isInstanceOf(SlotConflictException.class);
    }

    @Test
    void unknownAppointmentIsNotFound() {
        when(repo.findDetailsById(3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(ALICE, 3)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void onlyCustomerOrItsProviderMayViewAppointment() {
        when(repo.findDetailsById(3)).thenReturn(Optional.of(appt(3, 1, "BOOKED", NOW.plusDays(1))));

        assertThat(service.getAppointment(ALICE, 3).cancellable()).isTrue();
        assertThat(service.getAppointment(TUTOR, 3).cancellable()).isFalse();
        assertThatThrownBy(() -> service.getAppointment(BOB, 3)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void splitsUpcomingAndHistory() {
        when(repo.findByCustomer(1)).thenReturn(List.of(
                appt(1, 1, "BOOKED", NOW.plusDays(2)),
                appt(2, 1, "CANCELLED", NOW.plusDays(3)),
                appt(3, 1, "COMPLETED", NOW.minusDays(2))));

        AppointmentListDto list = service.getCustomerAppointments(1);

        assertThat(list.upcoming()).extracting("id").containsExactly(1L);
        assertThat(list.history()).extracting("id").containsExactly(2L, 3L);
    }
}
