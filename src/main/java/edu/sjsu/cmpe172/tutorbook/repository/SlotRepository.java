package edu.sjsu.cmpe172.tutorbook.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.dto.SlotSearchCriteria;
import edu.sjsu.cmpe172.tutorbook.model.SlotDetails;

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
