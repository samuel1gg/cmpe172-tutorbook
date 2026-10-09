package edu.sjsu.cmpe172.tutorbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.model.AppointmentDetails;

/** Data access for appointments using plain SQL over JDBC. */
@Repository
public class AppointmentRepository {

    private static final String SELECT_DETAILS = """
            SELECT a.appointment_id, a.status, a.notes, a.created_at,
                   u.user_id, u.full_name, u.email,
                   s.slot_id, s.provider_id, s.start_time, s.end_time,
                   p.display_name, sv.name AS service_name, sv.price_cents
            FROM appointments a
            JOIN users u               ON u.user_id = a.customer_id
            JOIN availability_slots s  ON s.slot_id = a.slot_id
            JOIN providers p           ON p.provider_id = s.provider_id
            JOIN services sv           ON sv.service_id = s.service_id
            """;

    private final JdbcClient jdbc;

    public AppointmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<AppointmentDetails> findDetailsById(long appointmentId) {
        return jdbc.sql(SELECT_DETAILS + " WHERE a.appointment_id = :id")
                .param("id", appointmentId)
                .query(AppointmentRepository::mapRow)
                .optional();
    }

    /** All of a customer's appointments, newest slot first ("my appointments"). */
    public List<AppointmentDetails> findByCustomer(long customerId) {
        return jdbc.sql(SELECT_DETAILS + " WHERE a.customer_id = :customerId ORDER BY s.start_time DESC")
                .param("customerId", customerId)
                .query(AppointmentRepository::mapRow)
                .list();
    }

    /** All appointments booked with a provider ("appointments booked with me"). */
    public List<AppointmentDetails> findByProvider(long providerId) {
        return jdbc.sql(SELECT_DETAILS + " WHERE s.provider_id = :providerId ORDER BY s.start_time DESC")
                .param("providerId", providerId)
                .query(AppointmentRepository::mapRow)
                .list();
    }

    /**
     * Inserts a BOOKED appointment and returns its id. If the slot already has
     * an active booking the UNIQUE(active_slot_id) constraint rejects the row
     * and Spring throws DuplicateKeyException (the database-level backstop).
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

    /**
     * BOOKED -> CANCELLED. The "AND status = 'BOOKED'" makes this a
     * compare-and-set: if two cancels race, only one changes a row.
     * Returns the number of rows changed (1 or 0).
     */
    public int cancel(long appointmentId) {
        return jdbc.sql("""
                UPDATE appointments
                SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
                WHERE appointment_id = :id AND status = 'BOOKED'
                """)
                .param("id", appointmentId)
                .update();
    }

    /** BOOKED -> COMPLETED for every appointment whose slot has ended. Returns rows changed. */
    public int markPastAsCompleted(LocalDateTime now) {
        return jdbc.sql("""
                UPDATE appointments
                SET status = 'COMPLETED'
                WHERE status = 'BOOKED'
                  AND slot_id IN (SELECT slot_id FROM availability_slots WHERE end_time <= :now)
                """)
                .param("now", now)
                .update();
    }

    private static AppointmentDetails mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AppointmentDetails(
                rs.getLong("appointment_id"),
                rs.getString("status"),
                rs.getString("notes"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getLong("user_id"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getLong("slot_id"),
                rs.getLong("provider_id"),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class),
                rs.getString("display_name"),
                rs.getString("service_name"),
                rs.getInt("price_cents"));
    }
}
