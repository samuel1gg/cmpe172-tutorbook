package edu.sjsu.cmpe172.tutorbook.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.model.ServiceOffering;

/** Data access for the services table using plain SQL over JDBC (no ORM). */
@Repository
public class ServiceOfferingRepository {

    private final JdbcClient jdbc;

    public ServiceOfferingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ServiceOffering> findAll() {
        return jdbc.sql("""
                SELECT service_id, name, description, duration_minutes, price_cents
                FROM services
                ORDER BY name
                """)
                .query((rs, rowNum) -> new ServiceOffering(
                        rs.getLong("service_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getInt("duration_minutes"),
                        rs.getInt("price_cents")))
                .list();
    }
}
