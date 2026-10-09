package edu.sjsu.cmpe172.tutorbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.model.ProviderSlotRow;
import edu.sjsu.cmpe172.tutorbook.model.SlotDetails;
import edu.sjsu.cmpe172.tutorbook.model.SlotState;

/**
 * Data access for availability_slots. A slot is "available" when it is in the
 * future and has no active (BOOKED) appointment, i.e. no appointments row whose
 * active_slot_id equals this slot's id.
 */
@Repository
public class SlotRepository {

    private static final String SELECT_COLUMNS = """
            SELECT s.slot_id, s.start_time, s.end_time,
                   p.provider_id, p.display_name,
                   sv.service_id, sv.name AS service_name, sv.duration_minutes, sv.price_cents
            """;

    private static final String FROM_AVAILABLE = """
            FROM availability_slots s
            JOIN providers p  ON p.provider_id = s.provider_id
            JOIN services  sv ON sv.service_id = s.service_id
            WHERE s.start_time > CURRENT_TIMESTAMP
              AND s.removed_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM appointments a WHERE a.active_slot_id = s.slot_id)
            """;

    private final JdbcClient jdbc;

    public SlotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of available slots matching the filters, soonest first (SQL LIMIT/OFFSET). */
    public List<SlotDetails> findAvailable(SlotSearchCriteria criteria, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        String sql = SELECT_COLUMNS + FROM_AVAILABLE + filterClause(criteria, params)
                + " ORDER BY s.start_time, s.slot_id LIMIT :limit OFFSET :offset";
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.sql(sql).params(params).query(SlotRepository::mapRow).list();
    }

    /** Total number of available slots matching the filters (for pagination). */
    public long countAvailable(SlotSearchCriteria criteria) {
        Map<String, Object> params = new HashMap<>();
        String sql = "SELECT COUNT(*) " + FROM_AVAILABLE + filterClause(criteria, params);
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    /** A single slot, only if it is still available. */
    public Optional<SlotDetails> findAvailableById(long slotId) {
        return jdbc.sql(SELECT_COLUMNS + FROM_AVAILABLE + " AND s.slot_id = :slotId")
                .param("slotId", slotId)
                .query(SlotRepository::mapRow)
                .optional();
    }

    // ------------------------------------------------------------------
    // Booking / concurrency support
    // ------------------------------------------------------------------

    /** Reads the slot's current state and version (step 1 of the optimistic check). */
    public Optional<SlotState> findState(long slotId) {
        return jdbc.sql("""
                SELECT s.slot_id, s.provider_id, s.start_time, s.version,
                       s.removed_at IS NOT NULL AS removed,
                       EXISTS (SELECT 1 FROM appointments a WHERE a.active_slot_id = s.slot_id) AS booked
                FROM availability_slots s
                WHERE s.slot_id = :slotId
                """)
                .param("slotId", slotId)
                .query((rs, rowNum) -> new SlotState(
                        rs.getLong("slot_id"),
                        rs.getLong("provider_id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getBoolean("removed"),
                        rs.getBoolean("booked"),
                        rs.getInt("version")))
                .optional();
    }

    /**
     * Optimistic-lock check (step 2): bump the version only if it is still the
     * value we read. Returns false when another transaction changed the slot
     * first (0 rows updated), which tells the caller to retry.
     */
    public boolean bumpVersion(long slotId, int expectedVersion) {
        int rows = jdbc.sql("""
                UPDATE availability_slots
                SET version = version + 1
                WHERE slot_id = :slotId AND version = :expectedVersion
                """)
                .param("slotId", slotId)
                .param("expectedVersion", expectedVersion)
                .update();
        return rows == 1;
    }

    // ------------------------------------------------------------------
    // Provider slot management
    // ------------------------------------------------------------------

    /** A provider's active upcoming slots, with the student who booked each one (if any). */
    public List<ProviderSlotRow> findUpcomingByProvider(long providerId) {
        return jdbc.sql("""
                SELECT s.slot_id, s.start_time, s.end_time, sv.name AS service_name,
                       a.appointment_id, u.full_name
                FROM availability_slots s
                JOIN services sv          ON sv.service_id = s.service_id
                LEFT JOIN appointments a  ON a.active_slot_id = s.slot_id
                LEFT JOIN users u         ON u.user_id = a.customer_id
                WHERE s.provider_id = :providerId
                  AND s.removed_at IS NULL
                  AND s.start_time > CURRENT_TIMESTAMP
                ORDER BY s.start_time
                """)
                .param("providerId", providerId)
                .query((rs, rowNum) -> new ProviderSlotRow(
                        rs.getLong("slot_id"),
                        rs.getObject("start_time", LocalDateTime.class),
                        rs.getObject("end_time", LocalDateTime.class),
                        rs.getString("service_name"),
                        rs.getObject("appointment_id", Long.class),
                        rs.getString("full_name")))
                .list();
    }

    /** True if the provider already has an active slot overlapping [start, end). */
    public boolean overlapExists(long providerId, LocalDateTime start, LocalDateTime end) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM availability_slots
                WHERE provider_id = :providerId
                  AND removed_at IS NULL
                  AND start_time < :end
                  AND end_time > :start
                """)
                .param("providerId", providerId)
                .param("start", start)
                .param("end", end)
                .query(Long.class)
                .single() > 0;
    }

    public long insert(long providerId, long serviceId, LocalDateTime start, LocalDateTime end) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO availability_slots (provider_id, service_id, start_time, end_time)
                VALUES (:providerId, :serviceId, :start, :end)
                """)
                .param("providerId", providerId)
                .param("serviceId", serviceId)
                .param("start", start)
                .param("end", end)
                .update(keys, "slot_id");
        return keys.getKey().longValue();
    }

    /** Soft delete: the row stays so cancelled appointments keep their history. */
    public int markRemoved(long slotId) {
        return jdbc.sql("""
                UPDATE availability_slots SET removed_at = CURRENT_TIMESTAMP
                WHERE slot_id = :slotId AND removed_at IS NULL
                """)
                .param("slotId", slotId)
                .update();
    }

    /** Appends optional filters as named parameters (never string-concatenated values). */
    private static String filterClause(SlotSearchCriteria c, Map<String, Object> params) {
        StringBuilder where = new StringBuilder();
        if (c.providerId() != null) {
            where.append(" AND s.provider_id = :providerId");
            params.put("providerId", c.providerId());
        }
        if (c.serviceId() != null) {
            where.append(" AND s.service_id = :serviceId");
            params.put("serviceId", c.serviceId());
        }
        if (c.date() != null) {
            where.append(" AND s.start_time >= :dayStart AND s.start_time < :dayEnd");
            params.put("dayStart", c.date().atStartOfDay());
            params.put("dayEnd", c.date().plusDays(1).atStartOfDay());
        }
        return where.toString();
    }

    private static SlotDetails mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SlotDetails(
                rs.getLong("slot_id"),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class),
                rs.getLong("provider_id"),
                rs.getString("display_name"),
                rs.getLong("service_id"),
                rs.getString("service_name"),
                rs.getInt("duration_minutes"),
                rs.getInt("price_cents"));
    }
}
