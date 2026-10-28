package edu.sjsu.cmpe172.tutorbook.notification;

import java.time.Instant;

/**
 * What the notification service answers.
 * status is ACCEPTED for a new notification or DUPLICATE when the
 * idempotency key was already seen (a retry of something it already has).
 */
public record NotificationResponse(String notificationId, String status, Instant receivedAt) {
}
