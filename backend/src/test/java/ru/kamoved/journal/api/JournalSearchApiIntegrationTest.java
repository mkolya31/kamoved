package ru.kamoved.journal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin")
@Sql(statements = {
    "DELETE FROM journal_payment",
    "DELETE FROM entry_contact",
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class JournalSearchApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void searchesEveryIncludedFieldAcrossAdditionalContactsAndItems() throws Exception {
        long id = createRichOrder();

        search("владимир", "all")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.items[0].matches[0].field").value("NAME"));
        search("нурик", "all")
            .andExpect(jsonPath("$.items[0].id").value(id));
        search("главнож", "all")
            .andExpect(jsonPath("$.items[0].matches[0].field").value("ADDRESS"));
        search("бордюр", "all")
            .andExpect(jsonPath("$.items[0].matches[0].field").value("ITEM"));
        search("ВЛАДИМИР готика", "all")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].matches.length()").value(2));
        search("владимир отсутствует", "all")
            .andExpect(jsonPath("$.totalItems").value(0));
        search("федор тест", "all")
            .andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.items[0].matches[0].additionalCount").value(1));
        search("федор камень", "all")
            .andExpect(jsonPath("$.items[0].matches[0].field").value("NAME"))
            .andExpect(jsonPath("$.items[0].matches[0].additionalCount").value(1))
            .andExpect(jsonPath("$.items[0].matches[1].field").value("ITEM"))
            .andExpect(jsonPath("$.items[0].matches[1].additionalCount").value(1));
    }

    @Test
    void canonicalizesPhoneFormatsAndSupportsPartialPhoneSearch() throws Exception {
        long id = createRichOrder();

        search("8 (999) 123-45-67", "all")
            .andExpect(jsonPath("$.items[0].id").value(id))
            .andExpect(jsonPath("$.items[0].matches[0].field").value("PHONE"))
            .andExpect(jsonPath("$.items[0].matches[0].value").value("+7 (999) 123-45-67"));
        search("1234567", "all")
            .andExpect(jsonPath("$.items[0].id").value(id));
        search("8 (999)", "all")
            .andExpect(jsonPath("$.items[0].id").value(id));
        search("9989898", "all")
            .andExpect(jsonPath("$.items[0].id").value(id));

        assertThat(jdbc.queryForObject(
            "SELECT normalized_phone FROM entry_contact WHERE journal_entry_id = ? AND phone LIKE '8%'",
            String.class,
            id
        )).isEqualTo("79989989898");
    }

    @Test
    void searchesEntryNumbersExactlyAndRespectsMode() throws Exception {
        long activeId = createOrder("Общий маркер", "Активный", "NEW", "служебный комментарий");
        long completedId = createOrder(
            "Общий маркер", "Завершённый", "COMPLETED", "другой комментарий");

        search("з " + activeId, "all")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(activeId))
            .andExpect(jsonPath("$.items[0].matches[0].field").value("ENTRY_NUMBER"));
        search("З-" + activeId + "0", "all")
            .andExpect(jsonPath("$.totalItems").value(0));
        search("общ мар", "active")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(activeId));
        search("общ мар", "all")
            .andExpect(jsonPath("$.totalItems").value(2));
        search("служебный", "all")
            .andExpect(jsonPath("$.totalItems").value(0));
        search("completed", "all")
            .andExpect(jsonPath("$.totalItems").value(0));
        search("1000", "all")
            .andExpect(jsonPath("$.totalItems").value(0));

        assertThat(completedId).isNotEqualTo(activeId);
    }

    @Test
    void returnsUniqueSortedPaginatedResultsWithExactMatchCounts() throws Exception {
        long oldest = createOrder("Серый маркер", "Иван", "NEW", null);
        long middle = createOrder("Серый маркер", "Иван", "NEW", null);
        long newest = createOrder("Серый маркер", "Иван", "NEW", null);
        jdbc.update("UPDATE journal_entry SET created_at = ? WHERE id = ?",
            OffsetDateTime.parse("2026-01-01T10:00:00+03:00"), oldest);
        jdbc.update("UPDATE journal_entry SET created_at = ? WHERE id = ?",
            OffsetDateTime.parse("2026-01-02T10:00:00+03:00"), middle);
        jdbc.update("UPDATE journal_entry SET created_at = ? WHERE id = ?",
            OffsetDateTime.parse("2026-01-03T10:00:00+03:00"), newest);

        mockMvc.perform(get("/api/journal/search")
                .param("query", "серый иван")
                .param("mode", "all")
                .param("page", "0")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(3))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].id").value(newest))
            .andExpect(jsonPath("$.items[1].id").value(middle));

        mockMvc.perform(get("/api/journal/search")
                .param("query", "серый иван")
                .param("mode", "all")
                .param("page", "1")
                .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(oldest));
    }

    @Test
    void refreshesSearchDocumentAfterFullOrderUpdate() throws Exception {
        long id = createOrder("Старое название", "Клиент", "NEW", null);
        search("старое", "all").andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(patch("/api/orders/{id}", id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [{"name": "Новое название", "quantity": 1, "unitPrice": 1000}],
                      "client": {"name": "Клиент"},
                      "additionalContacts": [],
                      "paymentStatus": "UNPAID",
                      "executionStatus": "NEW",
                      "version": 0
                    }
                    """))
            .andExpect(status().isOk());

        search("старое", "all").andExpect(jsonPath("$.totalItems").value(0));
        search("новое", "all")
            .andExpect(jsonPath("$.totalItems").value(1))
            .andExpect(jsonPath("$.items[0].id").value(id));
    }

    private org.springframework.test.web.servlet.ResultActions search(
        String query,
        String mode
    ) throws Exception {
        return mockMvc.perform(get("/api/journal/search")
                .param("query", query)
                .param("mode", mode))
            .andExpect(status().isOk());
    }

    private long createRichOrder() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {"name": "Готика Голд камень", "quantity": 1, "unitPrice": 1000},
                        {"name": "Бордюр серый камень", "quantity": 1, "unitPrice": 500}
                      ],
                      "client": {
                        "name": "Владимир Фёдор-Тест",
                        "phone": "+7 (999) 123-45-67",
                        "comment": "Фёдор-Тест только в комментарии"
                      },
                      "additionalContacts": [
                        {"name": "Нурик Фёдор-Тест", "phone": "8 (998) 998-98-98"}
                      ],
                      "executionStatus": "NEW",
                      "fulfillmentMethod": "DELIVERY_MARKET",
                      "deliveryAddress": "СНТ Главножуково",
                      "comment": "Не участвует в поиске"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        return id(result);
    }

    private long createOrder(
        String item,
        String client,
        String executionStatus,
        String comment
    ) throws Exception {
        String commentProperty = comment == null
            ? ""
            : ", \"comment\": \"" + comment + "\"";
        MvcResult result = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [{"name": "%s", "quantity": 1, "unitPrice": 1000}],
                      "client": {"name": "%s"},
                      "additionalContacts": [],
                      "executionStatus": "%s"%s
                    }
                    """.formatted(item, client, executionStatus, commentProperty)))
            .andExpect(status().isCreated())
            .andReturn();
        return id(result);
    }

    private long id(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return json.get("id").asLong();
    }
}
