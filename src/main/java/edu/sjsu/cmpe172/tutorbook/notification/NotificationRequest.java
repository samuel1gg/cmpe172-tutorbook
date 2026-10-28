package edu.sjsu.cmpe172.tutorbook.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The DTO that crosses the distribution boundary (TutorBook -> Notification Service).
 *
 * Coarse-grained: ONE request carries everything the remote service needs
 * (who, what kind, the finished message). The notification service never calls
 * back into TutorBook to look up the user, the slot or the provider, so a
 * notification costs exactly one network round trip.
 *
 * idempotencyKey ("appointment-12-BOOKING_CONFIRMED") is the same on every
 * retry of the same notification, so the service can drop duplicates.
 */
public record NotificationRequest(
        @NotBlank @Size(max = 100) String idempotencyKey,
        @NotNull Long appointmentId,
        @NotBlank @Size(max = 255) String recipient,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 1000) String message) {
}
