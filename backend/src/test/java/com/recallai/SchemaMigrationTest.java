package com.recallai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the full application against a real PostgreSQL container and verifies that
 * Flyway applied the schema. This is the foundation check: if this passes, the
 * datasource, migrations, JPA validation and security configuration all load.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTest {

    private static final List<String> EXPECTED_TABLES = List.of(
            "users", "decks", "cards", "review_history",
            "quizzes", "quiz_questions", "quiz_attempts", "ai_cache");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAllApplicationTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).containsAll(EXPECTED_TABLES);
        assertThat(tables).contains("flyway_schema_history");
    }

    @Test
    void flywayRecordsSuccessfulMigration() {
        Integer failed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = false", Integer.class);
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void cardsTableEnforcesSm2Invariants() {
        List<String> checks = jdbcTemplate.queryForList(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'cards'::regclass AND contype = 'c'",
                String.class);

        assertThat(checks).contains("ck_cards_ease_factor", "ck_cards_interval", "ck_cards_repetitions");
    }
}
