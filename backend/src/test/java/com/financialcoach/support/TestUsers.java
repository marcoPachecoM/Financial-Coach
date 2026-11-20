package com.financialcoach.support;

import com.financialcoach.users.UserRole;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates users for integration tests. Use inside a {@code @Transactional} test so they are
 * rolled back. Every user gets {@link #PASSWORD}.
 */
public final class TestUsers {

    public static final String PASSWORD = "TestPass123!";

    private final JdbcTemplate jdbc;
    private final String passwordHash;

    public TestUsers(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        // Hash once: Argon2 is deliberately slow.
        this.passwordHash = encoder.encode(PASSWORD);
    }

    public TestUser admin() {
        return create("admin", UserRole.ADMIN, true);
    }

    public TestUser client() {
        return create("client", UserRole.CLIENT, true);
    }

    public TestUser create(String username, UserRole role, boolean active) {
        String email = username + "@example.test";
        UUID id = jdbc.queryForObject("""
                INSERT INTO users (username, email, password_hash, role, is_active)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, UUID.class, username, email, passwordHash, role.name(), active);
        return new TestUser(id, email, PASSWORD, role);
    }

    public record TestUser(UUID id, String email, String password, UserRole role) {
    }
}
