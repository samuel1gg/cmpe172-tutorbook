package edu.sjsu.cmpe172.tutorbook.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * notification_log: TutorBook's own record of every notification it owes.
 * Status: PENDING -> SENT, or PENDING -> FAILED -> (retry job) -> SENT / GAVE_UP.
 */
@Repository
public class NotificationRepository {

    private final JdbcClient jdbc;

    public NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a PENDING row. Returns false if this idempotency key already
     * exists (UNIQUE constraint), i.e. the notification was already created.
     */
    public boolean insertPending(NotificationRequest r) {
        try {
            jdbc.sql("""
                    INSERT INTO notification_log
                        (idempotency_key, appointment_id, type, recipient, subject, message, status)
                    VALUES (:key, :appointmentId, :type, :recipient, :subject, :message, 'PENDING')
                    """)
                    .param("key", r.idempotencyKey())
                    .param("appointmentId", r.appointmentId())
                    .param("type", r.type().name())
                    .param("recipient", r.recipient())
                    .param("subject", r.subject())
                    .param("message", r.message())
                    .update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public Optional<NotificationRecord> findByKey(String key) {
        return jdbc.sql("SELECT * FROM notification_log WHERE idempotency_key = :key")
                .param("key", key)
                .query(NotificationRepository::mapRow)
                .optional();
    }

    public List<NotificationRecord> findByAppointment(long appointmentId) {
        return jdbc.sql("SELECT * FROM notification_log WHERE appointment_id = :id ORDER BY notification_id")
                .param("id", appointmentId)
                .query(NotificationRepository::mapRow)
                .list();
    }

    /** FAILED rows that still have attempts left, oldest first. */
    public List<NotificationRecord> findRetryable(int maxTotalAttempts, int limit) {
        return jdbc.sql("""
                SELECT * FROM notification_log
                WHERE status = 'FAILED' AND attempts < :max
                ORDER BY updated_at
                LIMIT :limit
                """)
                .param("max", maxTotalAttempts)
                .param("limit", limit)
                .query(NotificationRepository::mapRow)
                .list();
    }

    /** FAILED -> PENDING as a compare-and-set, so two retry runs never send the same row at once. */
    public boolean claimForRetry(String key) {
        return jdbc.sql("""
                UPDATE notification_log SET status = 'PENDING', updated_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = :key AND status = 'FAILED'
                """)
                .param("key", key)
                .update() == 1;
    }

    public void markSent(String key, int attempts) {
        jdbc.sql("""
                UPDATE notification_log
                SET status = 'SENT', attempts = attempts + :attempts, last_error = NULL,
                    sent_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = :key
                """)
                .param("key", key)
                .param("attempts", attempts)
                .update();
    }

    /** status is FAILED (will be retried) or GAVE_UP (will not). */
    public void markFailed(String key, int attempts, String status, String error) {
        jdbc.sql("""
                UPDATE notification_log
                SET status = :status, attempts = attempts + :attempts, last_error = :error,
                    updated_at = CURRENT_TIMESTAMP
                WHERE idempotency_key = :key
                """)
                .param("key", key)
                .param("attempts", attempts)
                .param("status", status)
                .param("error", error == null ? null : error.substring(0, Math.min(error.length(), 500)))
                .update();
    }

    public long countByStatus(String status) {
        return jdbc.sql("SELECT COUNT(*) FROM notification_log WHERE status = :status")
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private static NotificationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationRecord(
                rs.getLong("notification_id"),
                rs.getString("idempotency_key"),
                rs.getLong("appointment_id"),
                NotificationType.valueOf(rs.getString("type")),
                rs.getString("recipient"),
                rs.getString("subject"),
                rs.getString("message"),
                rs.getString("status"),
                rs.getInt("attempts"),
                rs.getString("last_error"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("sent_at", LocalDateTime.class));
    }
}
