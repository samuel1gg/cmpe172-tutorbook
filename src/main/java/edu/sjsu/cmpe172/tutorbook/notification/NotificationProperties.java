package edu.sjsu.cmpe172.tutorbook.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * notification.* settings from application.properties.
 *
 * baseUrl: where the notification service lives. Blank means "the mock
 *          controller inside this app" (http://localhost:<our port>).
 * Worst case with the defaults, a booking waits
 * 3 attempts x 1.5 s read timeout + 0.2 s + 0.4 s backoff = about 5 s
 * before giving up on the notification (the booking itself is already saved).
 */
@ConfigurationProperties("notification")
public record NotificationProperties(
        String baseUrl,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("1500ms") Duration readTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("200ms") Duration initialBackoff,
        @DefaultValue("12") int maxTotalAttempts) {
}
