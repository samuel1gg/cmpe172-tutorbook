package edu.sjsu.cmpe172.tutorbook.notification;

import java.time.LocalDateTime;

/** A notification_log row: one notification and its delivery state. */
public record NotificationRecord(
        long notificationId,
        String idempotencyKey,
        long appointmentId,
        NotificationType type,
        String recipient,
        String subject,
        String message,
        String status,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime sentAt) {

    public NotificationRequest toRequest() {
        return new NotificationRequest(idempotencyKey, appointmentId, recipient, type, subject, message);
    }
}
