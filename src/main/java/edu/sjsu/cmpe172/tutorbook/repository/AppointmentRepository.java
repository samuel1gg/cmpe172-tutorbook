package edu.sjsu.cmpe172.tutorbook.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;

/**
 * Data access for appointments. Milestone 1 only reads (confirmation page);
 * insert/cancel are here so the double-booking guard can be tested now and
 * reused by the booking feature in Milestone 2.
 */
@Repository
public class AppointmentRepository {

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppointmentDetails> findDetailsById(long appointmentId) {
        return jdbc.sql("""
                SELECT a.appointment_id, a.status, a.notes, a.created_at,
                       u.user_id, u.full_name, u.email,
                       s.slot_id, s.start_time, s.end_time,
                       p.display_name, sv.name AS service_name, sv.price_cents
                FROM appointments a
                JOIN users u               ON u.user_id = a.customer_id
                JOIN availability_slots s  ON s.slot_id = a.slot_id
                JOIN providers p           ON p.provider_id = s.provider_id
                JOIN services sv           ON sv.service_id = s.service_id
                WHERE a.appointment_id = :id
                """)
                .param("id", appointmentId)
                .query((rs, rowNum) -> new AppointmentDetails(
                        rs.getLong("appointment_id"),
                        rs.getString("status"),
                        rs.getString("notes"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getLong("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getLong("slot_id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getObject("end_time", LocalDateTime.class),
                        rs.getString("display_name"),
                        rs.getString("service_name"),
                        rs.getInt("price_cents")))
                .optional();
    }

    /**
     * Inserts a BOOKED appointment and returns its id. If the slot already has
     * an active booking the UNIQUE(active_slot_id) constraint rejects the row
     * and Spring throws DuplicateKeyException.
     */
    public long insertBooked(long slotId, long customerId, String notes) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO appointments (slot_id, customer_id, status, notes)
                VALUES (:slotId, :customerId, 'BOOKED', :notes)
                """)
                .param("slotId", slotId)
                .param("customerId", customerId)
                .param("notes", notes)
                .update(keys, "appointment_id");
        return keys.getKey().longValue();
    }

    /** Marks an appointment CANCELLED, which frees its slot. Returns rows changed. */
    public int cancel(long appointmentId) {
        return jdbc.sql("""
                UPDATE appointments
                SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
                WHERE appointment_id = :id AND status = 'BOOKED'
                """)
                .param("id", appointmentId)
                .update();
    }
}
