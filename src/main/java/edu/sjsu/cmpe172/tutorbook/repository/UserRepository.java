package edu.sjsu.cmpe172.tutorbook.repository;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import edu.sjsu.cmpe172.tutorbook.security.AppUser;

/** Data access for users (used by login). */
@Repository
public class UserRepository {

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Loads a user and, for tutors, their provider_id, in one query. */
    public Optional<AppUser> findByEmail(String email) {
        return jdbc.sql("""
                SELECT u.user_id, u.email, u.password_hash, u.full_name, u.role, p.provider_id
                FROM users u
                LEFT JOIN providers p ON p.user_id = u.user_id
                WHERE LOWER(u.email) = LOWER(:email)
                """)
                .param("email", email)
                .query((rs, rowNum) -> new AppUser(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getObject("provider_id", Long.class)))
                .optional();
    }
}
