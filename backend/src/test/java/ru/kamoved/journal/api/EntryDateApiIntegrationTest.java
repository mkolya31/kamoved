package ru.kamoved.journal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
@Sql(statements = {
    "DELETE FROM journal_payment", "DELETE FROM entry_contact",
    "DELETE FROM journal_entry_item", "DELETE FROM journal_entry"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class EntryDateApiIntegrationTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String payload(boolean order, boolean backdated, String date) throws Exception {
        var body = json.createObjectNode();
        body.putArray("items").addObject().put("name", "Дата маркер").put("quantity", 1).put("unitPrice", 5000);
        body.put("backdated", backdated);
        if (date != null) body.put("entryDate", date);
        if (order) body.putObject("initialPayment").put("amount", 1000).put("paymentMethod", "CASH");
        else body.put("paymentMethod", "CASH");
        return json.writeValueAsString(body);
    }

    private JsonNode create(boolean order, boolean backdated, String date) throws Exception {
        return json.readTree(mvc.perform(post(order ? "/api/orders" : "/api/sales").with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(payload(order, backdated, date)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    @Test
    void datesInitialPaymentsWithoutChangingTodayRevenueAndCountsLaterPaymentToday() throws Exception {
        String yesterday = LocalDate.now(MOSCOW).minusDays(1).toString();
        OffsetDateTime before = OffsetDateTime.now();
        JsonNode sale = create(false, true, yesterday);
        JsonNode order = create(true, true, yesterday);
        for (JsonNode entry : new JsonNode[]{sale, order}) {
            assertThat(entry.get("entryDate").asText()).isEqualTo(yesterday);
            assertThat(OffsetDateTime.parse(entry.get("createdAt").asText()).toInstant()).isAfterOrEqualTo(before.toInstant());
            JsonNode details = json.readTree(mvc.perform(get("/api/journal/{id}", entry.get("id").asLong()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            JsonNode payment = details.get("payments").get(0);
            assertThat(OffsetDateTime.parse(payment.get("receivedAt").asText()).atZoneSameInstant(MOSCOW).toLocalDate().toString()).isEqualTo(yesterday);
            assertThat(payment.get("receivedDateOnly").asBoolean()).isTrue();
        }
        mvc.perform(get("/api/journal")).andExpect(jsonPath("$.todayRevenue").value(0));
        mvc.perform(post("/api/orders/{id}/payments", order.get("id").asLong()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":500,\"paymentMethod\":\"CASH\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/journal")).andExpect(jsonPath("$.todayRevenue").value(500));
        create(false, false, yesterday);
        create(true, false, null);
        mvc.perform(get("/api/journal")).andExpect(jsonPath("$.todayRevenue").value(6500))
            .andExpect(jsonPath("$.items[0].entryDate").value(LocalDate.now(MOSCOW).toString()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsMissingInvalidTodayAndFutureDates(boolean order) throws Exception {
        for (String date : new String[]{null, "", "2026-02-31", "2026-09-0", "0000-01-01",
            LocalDate.now(MOSCOW).toString(), LocalDate.now(MOSCOW).plusDays(1).toString()}) {
            mvc.perform(post(order ? "/api/orders" : "/api/sales").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload(order, true, date)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM journal_entry", Integer.class)).isZero();
        create(order, true, "0001-01-01");
    }

    @Test
    void ordersJournalAndSearchByEventDateThenCreationTimeAndPreservesReminderPriority() throws Exception {
        String yesterday = LocalDate.now(MOSCOW).minusDays(1).toString();
        JsonNode today = create(true, false, null);
        JsonNode older = create(true, true, "2020-01-01");
        JsonNode first = create(true, true, yesterday);
        JsonNode last = create(true, true, yesterday);
        // Explicitly separate timestamps to verify ordering even with coarse database clocks.
        jdbc.update("UPDATE journal_entry SET created_at = ? WHERE id = ?", OffsetDateTime.now().minusSeconds(10), first.get("id").asLong());
        for (String url : new String[]{"/api/journal", "/api/journal/search?query=маркер", "/api/journal?mode=active"}) {
            mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(today.get("id").asLong()))
                .andExpect(jsonPath("$.items[1].id").value(last.get("id").asLong()))
                .andExpect(jsonPath("$.items[2].id").value(first.get("id").asLong()))
                .andExpect(jsonPath("$.items[3].id").value(older.get("id").asLong()));
        }
        jdbc.update("UPDATE journal_entry SET factory_ready_attention = TRUE, factory_ready_date = CURRENT_DATE WHERE id = ?", older.get("id").asLong());
        for (String url : new String[]{"/api/journal?mode=active", "/api/journal/search?mode=active&query=маркер"}) {
            mvc.perform(get(url)).andExpect(jsonPath("$.items[0].id").value(older.get("id").asLong()));
        }
    }
}
