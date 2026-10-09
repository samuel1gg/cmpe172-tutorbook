package edu.sjsu.cmpe172.tutorbook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.exception.OptimisticConflictException;
import edu.sjsu.cmpe172.tutorbook.exception.SlotConflictException;

/** Unit tests for the retry loop around the booking transaction. */
class BookingServiceTest {

    BookingTransaction tx = mock(BookingTransaction.class);
    BookingService service = new BookingService(tx);

    @Test
    void retriesAfterAVersionConflictAndSucceeds() {
        when(tx.tryBook(1, 7, null))
                .thenThrow(new OptimisticConflictException(7))
                .thenReturn(99L);

        assertThat(service.book(1, 7, null)).isEqualTo(99L);
        verify(tx, times(2)).tryBook(1, 7, null);
    }

    @Test
    void givesUpWith409AfterMaxAttempts() {
        when(tx.tryBook(1, 7, null)).thenThrow(new OptimisticConflictException(7));

        assertThatThrownBy(() -> service.book(1, 7, null)).isInstanceOf(SlotConflictException.class);
        verify(tx, times(BookingService.MAX_ATTEMPTS)).tryBook(1, 7, null);
    }

    @Test
    void doesNotRetryARealConflict() {
        when(tx.tryBook(1, 7, null)).thenThrow(new SlotConflictException("booked"));

        assertThatThrownBy(() -> service.book(1, 7, null)).isInstanceOf(SlotConflictException.class);
        verify(tx, times(1)).tryBook(1, 7, null);
    }
}
