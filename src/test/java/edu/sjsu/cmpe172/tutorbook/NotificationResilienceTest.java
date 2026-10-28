package edu.sjsu.cmpe172.tutorbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

import edu.sjsu.cmpe172.tutorbook.mocknotification.MockNotificationService;
import edu.sjsu.cmpe172.tutorbook.mocknotification.MockNotificationService.Mode;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationRecord;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationRepository;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationRetryJob;
import edu.sjsu.cmpe172.tutorbook.notification.NotificationType;
import edu.sjsu.cmpe172.tutorbook.security.AppUser;
import edu.sjsu.cmpe172.tutorbook.service.AppointmentService;
import edu.sjsu.cmpe172.tutorbook.service.BookingService;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Integration tests for the distribution boundary, over REAL HTTP:
 * the app runs on a random port and HttpNotificationGateway calls the mock
 * notification controller through the network stack, with short timeouts.
 *
 * The key requirement: a booking still succeeds when the notification
 * service is down or slow, and the notification is recovered later.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "notification.connect-timeout=300ms",
        "notification.read-timeout=400ms",
        "notification.initial-backoff=20ms",
        "notification.retry-interval=1h",       // the test runs the retry job by hand
        "notification.mock.slow-delay=1s"})     // longer than the read timeout
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationResilienceTest {

    static final AppUser ALICE = new AppUser(1, "alice@student.sjsu.edu", "x", "Alice Nguyen", "CUSTOMER", null);

    @LocalServerPort
    int port;

    @Autowired
    BookingService bookingService;

    @Autowired
    AppointmentService appointmentService;

    @Autowired
    MockNotificationService mockService;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    NotificationRetryJob retryJob;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MeterRegistry meters;

    @BeforeEach
    void resetMock() {
        mockService.clear();
        mockService.setMode(Mode.UP);
    }

    @Test
    void bookAndCancel_eachSendOneConfirmation_whenServiceIsUp() {
        long id = bookingService.book(ALICE.userId(), 1, "derivatives");

        assertThat(notification(id, NotificationType.BOOKING_CONFIRMED).status()).isEqualTo("SENT");
        assertThat(mockService.all()).hasSize(1);
        assertThat(mockService.all().get(0).request().recipient()).isEqualTo("alice@student.sjsu.edu");
        assertThat(mockService.all().get(0).request().message()).contains("is confirmed");

        appointmentService.cancel(ALICE, id);

        assertThat(notification(id, NotificationType.BOOKING_CANCELLED).status()).isEqualTo("SENT");
        assertThat(mockService.all()).extracting(d -> d.request().type())
                .containsExactly(NotificationType.BOOKING_CONFIRMED, NotificationType.BOOKING_CANCELLED);
    }

    /** THE REQUIRED TEST: notification stub is down, booking still succeeds. */
    @Test
    void bookingSucceeds_whenNotificationServiceIsDown() {
        mockService.setMode(Mode.DOWN); // every request gets 503
        double failedBefore = counter("tutorbook.notifications", "outcome", "failed");

        long id = bookingService.book(ALICE.userId(), 3, null);

        // the booking is committed...
        assertThat(appointmentStatus(id)).isEqualTo("BOOKED");
        // ...the gateway tried 3 times, then fell back to saving the notification as FAILED
        NotificationRecord n = notification(id, NotificationType.BOOKING_CONFIRMED);
        assertThat(n.status()).isEqualTo("FAILED");
        assertThat(n.attempts()).isEqualTo(3);
        assertThat(n.lastError()).isEqualTo("HTTP 503");
        assertThat(mockService.requestsReceived()).isEqualTo(3);
        assertThat(mockService.all()).isEmpty();
        assertThat(counter("tutorbook.notifications", "outcome", "failed")).isEqualTo(failedBefore + 1);
    }

    /** Recovery: once the service is back, NotificationRetryJob delivers the FAILED notification. */
    @Test
    void failedNotificationIsDeliveredByRetryJob_whenServiceComesBack() {
        mockService.setMode(Mode.DOWN);
        long id = bookingService.book(ALICE.userId(), 6, null);
        assertThat(notification(id, NotificationType.BOOKING_CONFIRMED).status()).isEqualTo("FAILED");

        mockService.setMode(Mode.UP);
        retryJob.retryFailed();

        NotificationRecord n = notification(id, NotificationType.BOOKING_CONFIRMED);
        assertThat(n.status()).isEqualTo("SENT");
        assertThat(n.attempts()).isEqualTo(4); // 3 failed + 1 successful
        assertThat(copiesReceived(id)).isEqualTo(1);
    }

    /**
     * Slow service: every attempt times out, but the slow service DID receive
     * the requests. Because all retries carry the same idempotency key, it
     * keeps exactly one copy.
     */
    @Test
    void bookingSucceeds_whenNotificationServiceIsSlow_andRetriesDoNotDuplicate() throws Exception {
        mockService.setMode(Mode.SLOW);

        long start = System.nanoTime();
        long id = bookingService.book(ALICE.userId(), 4, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(appointmentStatus(id)).isEqualTo("BOOKED");
        assertThat(notification(id, NotificationType.BOOKING_CONFIRMED).lastError()).isEqualTo("timeout");
        assertThat(elapsedMs).isLessThan(5_000); // bounded by the timeouts, not by the slow service

        Thread.sleep(1_500); // let the slow service finish handling the 3 timed-out requests
        assertThat(mockService.requestsReceived()).isEqualTo(3);
        assertThat(copiesReceived(id)).isEqualTo(1); // idempotency: 3 requests, 1 notification

        mockService.setMode(Mode.UP);
        retryJob.retryFailed(); // service answers DUPLICATE, which still counts as delivered
        assertThat(notification(id, NotificationType.BOOKING_CONFIRMED).status()).isEqualTo("SENT");
        assertThat(copiesReceived(id)).isEqualTo(1);
    }

    /** How many copies of this appointment's confirmation the external service holds. */
    private long copiesReceived(long appointmentId) {
        return mockService.all().stream()
                .filter(d -> d.request().appointmentId() == appointmentId)
                .count();
    }

    @Test
    void mockServiceIsIdempotent() {
        RestClient http = RestClient.create("http://localhost:" + port);
        String json = """
                {"idempotencyKey":"test-key-1","appointmentId":1,"recipient":"a@b.edu",
                 "type":"BOOKING_CONFIRMED","subject":"s","message":"m"}""";

        ResponseEntity<Map> first = http.post().uri("/mock-notification-service/notifications")
                .contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(Map.class);
        ResponseEntity<Map> second = http.post().uri("/mock-notification-service/notifications")
                .contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(Map.class);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getBody()).containsEntry("status", "ACCEPTED");
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getBody()).containsEntry("status", "DUPLICATE");
        assertThat(mockService.all()).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void healthEndpointReportsDatabase() {
        ResponseEntity<Map> response = RestClient.create("http://localhost:" + port)
                .get().uri("/health").retrieve().toEntity(Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat((String) response.getBody().get("status")).isIn("UP", "DEGRADED");
        assertThat((Map<String, Object>) response.getBody().get("database")).containsEntry("status", "UP");
        assertThat(response.getBody()).containsKey("notifications");
    }

    @Test
    void bookingMetricsAreRecorded() {
        double before = counter("tutorbook.bookings", "outcome", "success");

        bookingService.book(ALICE.userId(), 7, null);

        assertThat(counter("tutorbook.bookings", "outcome", "success")).isEqualTo(before + 1);
        assertThat(meters.get("tutorbook.booking.latency").timer().count()).isGreaterThanOrEqualTo(1);
        assertThat(meters.get("tutorbook.bookings.last_hour").gauge().value()).isGreaterThanOrEqualTo(1);
    }

    private NotificationRecord notification(long appointmentId, NotificationType type) {
        return notifications.findByKey("appointment-" + appointmentId + "-" + type.name()).orElseThrow();
    }

    private String appointmentStatus(long id) {
        return jdbc.sql("SELECT status FROM appointments WHERE appointment_id = :id")
                .param("id", id).query(String.class).single();
    }

    private double counter(String name, String tag, String value) {
        var c = meters.find(name).tag(tag, value).counter();
        return c == null ? 0 : c.count();
    }
}
