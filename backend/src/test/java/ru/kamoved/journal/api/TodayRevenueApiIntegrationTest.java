package ru.kamoved.journal.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TodayRevenueApiIntegrationTest.FixedClockConfiguration.class)
@Sql(statements = {
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry",
    "DELETE FROM app_user WHERE username <> 'admin'"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(statements = {
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry",
    "DELETE FROM app_user WHERE username <> 'admin'"
}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class TodayRevenueApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(username = "admin")
    void returnsPaidCompletedSalesForCurrentSellerAndMoscowDay() throws Exception {
        long adminId = userId("admin");
        long anotherSellerId = createUser("another-seller");

        createEntry(adminId, "SALE", "PAID", "COMPLETED", "100.00", "2026-08-12T21:00:00Z");
        createEntry(adminId, "SALE", "PAID", "COMPLETED", "250.50", "2026-08-13T20:59:59Z");

        createEntry(adminId, "SALE", "PAID", "COMPLETED", "10.00", "2026-08-12T20:59:59Z");
        createEntry(adminId, "SALE", "PAID", "COMPLETED", "20.00", "2026-08-13T21:00:00Z");
        createEntry(adminId, "SALE", "UNPAID", "COMPLETED", "30.00", "2026-08-13T10:00:00Z");
        createEntry(adminId, "SALE", "PAID", "CANCELLED", "40.00", "2026-08-13T11:00:00Z");
        createEntry(adminId, "ORDER", "PAID", "COMPLETED", "50.00", "2026-08-13T12:00:00Z");
        createEntry(anotherSellerId, "SALE", "PAID", "COMPLETED", "60.00", "2026-08-13T13:00:00Z");

        mockMvc.perform(get("/api/journal?page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.todayRevenue").value(350.5));
    }

    private long userId(String username) {
        return jdbc.queryForObject(
            "SELECT id FROM app_user WHERE username = ?",
            Long.class,
            username
        );
    }

    private long createUser(String username) {
        jdbc.update("""
            INSERT INTO app_user (username, password_hash, display_name, active, created_at)
            VALUES (?, 'not-used', ?, TRUE, CURRENT_TIMESTAMP)
            """, username, username);
        return userId(username);
    }

    private void createEntry(
        long userId,
        String type,
        String paymentStatus,
        String executionStatus,
        String totalAmount,
        String createdAt
    ) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC);
        jdbc.update("""
            INSERT INTO journal_entry (
                type,
                execution_status,
                payment_status,
                total_amount,
                created_by,
                created_at,
                updated_at,
                version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 0)
            """,
            type,
            executionStatus,
            paymentStatus,
            new BigDecimal(totalAmount),
            userId,
            timestamp,
            timestamp
        );
    }

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-13T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
