package edu.sjsu.cmpe172.tutorbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import edu.sjsu.cmpe172.tutorbook.exception.NotFoundException;
import edu.sjsu.cmpe172.tutorbook.exception.OptimisticConflictException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;
import edu.sjsu.cmpe172.tutorbook.model.SlotState;
import edu.sjsu.cmpe172.tutorbook.repository.AppointmentRepository;
import edu.sjsu.cmpe172.tutorbook.repository.SlotRepository;

/** Unit tests (no Spring, no database) for the booking rules in one transaction. */
class BookingTransactionTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 8, 12, 0);
    static final long CUSTOMER = 1, SLOT = 7;

    SlotRepository slots = mock(SlotRepository.class);
    AppointmentRepository appointments = mock(AppointmentRepository.class);
    BookingTransaction tx;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        tx = new BookingTransaction(slots, appointments, clock);
    }

    static SlotState slot(LocalDateTime start, boolean removed, boolean booked, int version) {
        return new SlotState(SLOT, 2, start, removed, booked, version);
    }

    @Test
    void booksAnOpenFutureSlot() {
        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.plusDays(1), false, false, 4)));
        when(slots.bumpVersion(SLOT, 4)).thenReturn(true);
        when(appointments.insertBooked(SLOT, CUSTOMER, "notes")).thenReturn(55L);

        assertThat(tx.tryBook(CUSTOMER, SLOT, "notes")).isEqualTo(55L);
        verify(slots).bumpVersion(SLOT, 4); // version check used the value that was read
    }

    @Test
    void alreadyBookedSlotIsAConflict() {
        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.plusDays(1), false, true, 4)));

        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(SlotConflictException.class);
        verify(appointments, never()).insertBooked(anyLong(), anyLong(), any());
    }

    @Test
    void pastSlotCannotBeBooked() {
        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.minusHours(1), false, false, 0)));

        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(SlotConflictException.class);
        verify(slots, never()).bumpVersion(anyLong(), anyInt());
    }

    @Test
    void missingOrRemovedSlotIsNotFound() {
        when(slots.findState(SLOT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(NotFoundException.class);

        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.plusDays(1), true, false, 0)));
        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void versionChangedByAnotherTransactionSignalsRetry() {
        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.plusDays(1), false, false, 4)));
        when(slots.bumpVersion(SLOT, 4)).thenReturn(false); // someone else bumped it first

        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(OptimisticConflictException.class);
        verify(appointments, never()).insertBooked(anyLong(), anyLong(), any());
    }

    @Test
    void uniqueConstraintBackstopBecomesConflict() {
        when(slots.findState(SLOT)).thenReturn(Optional.of(slot(NOW.plusDays(1), false, false, 4)));
        when(slots.bumpVersion(SLOT, 4)).thenReturn(true);
        when(appointments.insertBooked(SLOT, CUSTOMER, null)).thenThrow(new DuplicateKeyException("uq_appt_active_slot"));

        assertThatThrownBy(() -> tx.tryBook(CUSTOMER, SLOT, null)).isInstanceOf(SlotConflictException.class);
    }
}
