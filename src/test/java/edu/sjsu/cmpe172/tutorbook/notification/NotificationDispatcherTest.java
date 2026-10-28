package edu.sjsu.cmpe172.tutorbook.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;

/** Unit tests for building the coarse-grained DTO and masking emails in logs. */
class NotificationDispatcherTest {

    static final AppointmentDetails APPT = new AppointmentDetails(12L, "BOOKED", null, LocalDateTime.now(),
            1L, "Alice Nguyen", "alice@student.sjsu.edu", 7L, 2L,
            LocalDateTime.of(2026, 10, 30, 15, 0), LocalDateTime.of(2026, 10, 30, 16, 0),
            "Dr. Priya Patel", "Calculus Tutoring", 4500);

    @Test
    void confirmationCarriesEverythingTheRemoteServiceNeeds() {
        NotificationRequest r = NotificationDispatcher.build(APPT, NotificationType.BOOKING_CONFIRMED);

        assertThat(r.idempotencyKey()).isEqualTo("appointment-12-BOOKING_CONFIRMED");
        assertThat(r.recipient()).isEqualTo("alice@student.sjsu.edu");
        assertThat(r.subject()).isEqualTo("Confirmed: Calculus Tutoring on Fri, Oct 30 at 3:00 PM");
        assertThat(r.message()).contains("Alice Nguyen", "Dr. Priya Patel", "is confirmed", "#12");
    }

    @Test
    void cancellationHasItsOwnKeySoItIsNotMistakenForADuplicate() {
        NotificationRequest r = NotificationDispatcher.build(APPT, NotificationType.BOOKING_CANCELLED);

        assertThat(r.idempotencyKey()).isEqualTo("appointment-12-BOOKING_CANCELLED");
        assertThat(r.message()).contains("has been cancelled");
    }

    @Test
    void emailsAreMaskedInLogs() {
        assertThat(NotificationDispatcher.mask("alice@student.sjsu.edu")).isEqualTo("a***@student.sjsu.edu");
        assertThat(NotificationDispatcher.mask("a@x.edu")).isEqualTo("***@x.edu");
    }
}
