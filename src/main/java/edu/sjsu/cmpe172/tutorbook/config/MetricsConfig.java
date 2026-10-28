package edu.sjsu.cmpe172.tutorbook.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import edu.sjsu.cmpe172.tutorbook.notification.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Custom metrics, read at GET /actuator/metrics/{name}:
 *
 *   tutorbook.bookings{outcome}          counter: success / conflict / not_found  (BookingService)
 *   tutorbook.booking.latency            timer: how long a booking takes            (BookingService)
 *   tutorbook.notifications{type,outcome} counter: sent / failed / gave_up          (NotificationDispatcher)
 *   tutorbook.bookings.last_hour         gauge: bookings created in the last hour   (below)
 *   tutorbook.notifications.backlog      gauge: notifications waiting for a retry   (below)
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class MetricsConfig {

    @Bean
    public MeterBinder tutorbookGauges(JdbcClient jdbc) {
        return registry -> {
            Gauge.builder("tutorbook.bookings.last_hour", () -> jdbc.sql("""
                            SELECT COUNT(*) FROM appointments
                            WHERE created_at >= DATEADD('HOUR', -1, CURRENT_TIMESTAMP)
                            """).query(Long.class).single())
                    .description("Appointments booked in the last 60 minutes")
                    .register(registry);
            Gauge.builder("tutorbook.notifications.backlog", () -> jdbc.sql(
                            "SELECT COUNT(*) FROM notification_log WHERE status = 'FAILED'")
                            .query(Long.class).single())
                    .description("Notifications waiting for NotificationRetryJob")
                    .register(registry);
        };
    }
}
