package edu.sjsu.cmpe172.tutorbook.repository;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.model.Provider;

/** Data access for the providers table using plain SQL over JDBC (no ORM). */
@Repository
public class ProviderRepository {

    private final JdbcClient jdbc;

    public ProviderRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Provider> findAll() {
        return jdbc.sql("""
                SELECT provider_id, user_id, display_name, specialty, bio
                FROM providers
                ORDER BY display_name
                """)
                .query((rs, rowNum) -> new Provider(
                        rs.getLong("provider_id"),
                        rs.getLong("user_id"),
                        rs.getString("display_name"),
                        rs.getString("specialty"),
                        rs.getString("bio")))
                .list();
    }
}
