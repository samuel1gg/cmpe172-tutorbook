package edu.sjsu.cmpe172.tutorbook.controller;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.sjsu.cmpe172.tutorbook.notification.NotificationRepository;

/**
 * GET /health
 *
 *   database             runs SELECT 1; if it fails the whole app is DOWN (503),
 *                        because nothing works without the database.
 *   notifications        how many notifications are waiting for a retry.
 *                        A backlog makes the app DEGRADED (still 200): bookings
 *                        work, only the confirmations are late.
 *
 * Docker's HEALTHCHECK and a load balancer can poll this URL.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcClient jdbc;
    private final NotificationRepository notificationRepository;

    public HealthController(JdbcClient jdbc, NotificationRepository notificationRepository) {
        this.jdbc = jdbc;
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> database = checkDatabase();
        boolean dbUp = "UP".equals(database.get("status"));

        Map<String, Object> notifications = new LinkedHashMap<>();
        long waiting = 0;
        if (dbUp) {
            waiting = notificationRepository.countByStatus("FAILED");
            notifications.put("status", waiting == 0 ? "UP" : "DEGRADED");
            notifications.put("waitingForRetry", waiting);
            notifications.put("gaveUp", notificationRepository.countByStatus("GAVE_UP"));
        } else {
            notifications.put("status", "UNKNOWN");
        }

        String overall = !dbUp ? "DOWN" : waiting > 0 ? "DEGRADED" : "UP";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", overall);
        body.put("timestamp", Instant.now().toString());
        body.put("uptimeSeconds", Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()).toSeconds());
        body.put("database", database);
        body.put("notifications", notifications);
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private Map<String, Object> checkDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        long start = System.nanoTime();
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            result.put("status", "UP");
        } catch (RuntimeException e) {
            log.error("Health check: database is unreachable ({})", e.getMessage());
            result.put("status", "DOWN");
            result.put("error", e.getClass().getSimpleName());
        }
        result.put("responseTimeMs", (System.nanoTime() - start) / 1_000_000);
        return result;
    }
}
