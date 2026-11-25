package com.financialcoach.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.financialcoach.TestcontainersConfiguration;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * uuidv7() behaves the same on PostgreSQL 17 (the function created by db/migration/beforeMigrate.sql)
 * and 18 (built in). CI runs this suite on both versions.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UuidV7Test {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void generatesVersion7UuidsWithTheRfcVariant() {
        assertThat(jdbc.queryForObject("SELECT pg_typeof(uuidv7())::text", String.class)).isEqualTo("uuid");
        assertThat(jdbc.queryForObject("SELECT uuid_extract_version(uuidv7())", Integer.class)).isEqualTo(7);

        List<UUID> ids = jdbc.queryForList("SELECT uuidv7() FROM generate_series(1, 200)", UUID.class);
        assertThat(ids).doesNotHaveDuplicates()
                .allSatisfy(id -> {
                    assertThat(id.version()).isEqualTo(7);
                    assertThat(id.variant()).isEqualTo(2); // RFC 9562: 10xx
                });
    }

    @Test
    void startsWithTheCurrentTimeSoValuesSortByCreation() throws InterruptedException {
        // The first 48 bits are the Unix time in ms. Read by hand: uuid_extract_timestamp() only
        // understands version 7 from PostgreSQL 18 on (on 17 it returns null for it).
        long embeddedMillis = jdbc.queryForObject(
                "SELECT ('x' || substr(replace(uuidv7()::text, '-', ''), 1, 12))::bit(48)::bigint", Long.class);
        OffsetDateTime now = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        assertThat(Duration.ofMillis(Math.abs(now.toInstant().toEpochMilli() - embeddedMillis)))
                .isLessThan(Duration.ofSeconds(1));

        UUID first = jdbc.queryForObject("SELECT uuidv7()", UUID.class);
        Thread.sleep(5);
        UUID second = jdbc.queryForObject("SELECT uuidv7()", UUID.class);
        assertThat(jdbc.queryForObject("SELECT ?::uuid < ?::uuid", Boolean.class, first, second)).isTrue();
    }

    @Test
    void theBuiltInFunctionIsUsedWhenPostgresHasOne() {
        boolean builtIn = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'uuidv7' AND pronamespace = 'pg_catalog'::regnamespace)",
                Boolean.class));
        boolean polyfill = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'uuidv7' AND pronamespace = 'public'::regnamespace)",
                Boolean.class));
        // Exactly one of them: the polyfill only where PostgreSQL lacks the function (before 18).
        assertThat(polyfill).isNotEqualTo(builtIn);
    }
}
