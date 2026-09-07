package ru.kamoved.journal.application;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntryDateMigrationTest {
    @Test
    void backfillsMoscowDatesWithoutChangingExistingEntriesOrPayments() {
        // Unlike PostgreSQL, H2 converts a zoned timestamp back to the session zone
        // when casting it to DATE. Use Moscow for H2 sessions in this migration test.
        var source = new DriverManagerDataSource(
            "jdbc:h2:mem:entry-date-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                + ";INIT=SET TIME ZONE 'Europe/Moscow'", "sa", "");
        Flyway.configure().dataSource(source).target("12").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("""
            INSERT INTO app_user (id, username, password_hash, display_name, created_at)
            VALUES (1, 'migration', 'unused', 'Migration', CURRENT_TIMESTAMP)
            """);
        jdbc.update("""
            INSERT INTO journal_entry (id, type, execution_status, payment_status, total_amount,
                created_by, created_at, updated_at)
            VALUES (1, 'SALE', 'COMPLETED', 'PAID', 5000, 1,
                TIMESTAMP WITH TIME ZONE '2026-09-06 21:30:00+00:00',
                TIMESTAMP WITH TIME ZONE '2026-09-06 21:30:00+00:00')
            """);
        jdbc.update("""
            INSERT INTO journal_payment (journal_entry_id, amount, payment_method, received_at, created_by, created_at)
            VALUES (1, 5000, 'CASH', TIMESTAMP WITH TIME ZONE '2026-09-06 20:59:00+00:00', 1,
                TIMESTAMP WITH TIME ZONE '2026-09-06 21:30:00+00:00')
            """);
        var entryBefore = jdbc.queryForMap("SELECT created_at, updated_at FROM journal_entry");
        var paymentBefore = jdbc.queryForMap("SELECT received_at, created_at, amount FROM journal_payment");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForObject("SELECT entry_date FROM journal_entry", LocalDate.class))
            .isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(jdbc.queryForMap("SELECT created_at, updated_at FROM journal_entry")).isEqualTo(entryBefore);
        assertThat(jdbc.queryForMap("SELECT received_at, created_at, amount FROM journal_payment")).isEqualTo(paymentBefore);
        assertThat(jdbc.queryForObject("SELECT received_date_only FROM journal_payment", Boolean.class)).isFalse();
    }
}
