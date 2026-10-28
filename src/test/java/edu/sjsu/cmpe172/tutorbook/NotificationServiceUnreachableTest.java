package edu.sjsu.cmpe172.tutorbook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;

import edu.sjsu.cmpe172.tutorbook.notification.NotificationRecord;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationRepository;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;

/**
 * The notification service is not just failing, it does not exist: the
 * gateway points at a port where nothing is listening (connection refused).
 * The booking must still succeed.
 */
@SpringBootTest(properties = {
        "notification.base-url=http://localhost:1",
        "notification.initial-backoff=20ms",
        "notification.retry-interval=1h"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationServiceUnreachableTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    JdbcClient jdbc;

    @Test
    void bookingSucceeds_whenNotificationServiceIsUnreachable() {
        long id = bookingService.book(1, 1, null);

        String status = jdbc.sql("SELECT status FROM appointments WHERE appointment_id = :id")
                .param("id", id).query(String.class).single();
        assertThat(status).isEqualTo("BOOKED");

        NotificationRecord n = notifications.findByKey("appointment-" + id + "-BOOKING_CONFIRMED").orElseThrow();
        assertThat(n.status()).isEqualTo("FAILED");
        assertThat(n.attempts()).isEqualTo(3);
        assertThat(n.lastError()).isEqualTo("connection refused");
    }
}
