package ru.kamoved.journal.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import ru.kamoved.notification.persistence.EmailNotificationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "kamoved.users[0].username=admin",
    "kamoved.users[0].password=test-password",
    "kamoved.users[0].display-name=Тестовый пользователь",
    "kamoved.users[0].email=admin@example.test",
    "kamoved.users[0].notifications=FACTORY_READY",
    "kamoved.factory-ready.scan-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Import(FactoryReadyReminderIntegrationTest.ClockConfiguration.class)
@Sql(statements = {
    "DELETE FROM email_notification",
    "DELETE FROM journal_payment",
    "DELETE FROM entry_contact",
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FactoryReadyReminderIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-29T06:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FactoryReadyReminderService reminders;
    @Autowired private EmailNotificationRepository notifications;
    @Autowired private FactoryReadyEmailDeliveryGuard deliveryGuard;
    @Autowired private MutableClock clock;

    @Test
    @WithMockUser(username = "admin")
    void activatesAtNineAndEnqueuesOnlyOneEmailPerDay() throws Exception {
        var result = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [{"name": "Плитка", "quantity": 12, "unit": "SQUARE_METER", "unitPrice": 1000}],
                      "additionalContacts": [],
                      "executionStatus": "IN_PRODUCTION",
                      "factoryReadyDate": "2026-09-03"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.factoryReadyAttention").value(false))
            .andReturn();
        long orderId = objectMapper.readTree(result.getResponse().getContentAsByteArray())
            .get("id").asLong();

        clock.advance(Duration.ofDays(2).plusHours(23));
        reminders.processDueReminders();
        assertThat(notifications.count()).isZero();

        clock.advance(Duration.ofDays(1));
        reminders.processDueReminders();
        reminders.processDueReminders();

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.factoryReadyAttention").value(true));
        assertThat(notifications.count()).isEqualTo(1);
        String notificationKey = notifications.findAll().getFirst().getNotificationKey();
        assertThat(notificationKey)
            .contains("FACTORY_READY:" + orderId + ":2026-09-03:2026-09-02:admin");
        assertThat(deliveryGuard.shouldDeliver(notificationKey)).isTrue();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/orders/{id}/factory-ready/confirm", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\": 1}"))
            .andExpect(status().isOk());
        assertThat(deliveryGuard.shouldDeliver(notificationKey)).isFalse();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary
        MutableClock factoryReadyTestClock() { return new MutableClock(INITIAL_TIME); }
    }

    static final class MutableClock extends Clock {
        private Instant instant;
        MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
