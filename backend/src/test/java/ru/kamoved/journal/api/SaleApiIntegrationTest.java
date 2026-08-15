package ru.kamoved.journal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
    "DELETE FROM entry_contact",
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SaleApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "admin")
    void createsCompletedPaidSaleAndShowsItFirstInJournal() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/sales")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {
                          "name": "Готика Голд Кристалл",
                          "quantity": 30.1,
                          "unit": "SQUARE_METER",
                          "unitPrice": 2855
                        }
                      ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("SALE"))
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.executionStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.totalAmount").value(85935))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        long saleId = created.get("id").asLong();

        mockMvc.perform(get("/api/journal"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].mainItem.name")
                .value("Готика Голд Кристалл"))
            .andExpect(jsonPath("$.items[0].totalAmount").value(85935));

        mockMvc.perform(get("/api/journal/{id}", saleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Готика Голд Кристалл"))
            .andExpect(jsonPath("$.items[0].lineTotal").value(85935));

        mockMvc.perform(get("/api/journal?mode=active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @WithMockUser(username = "admin")
    void savesTrimmedCommentAndNormalizesBlankCommentToNull() throws Exception {
        MvcResult commentedSale = mockMvc.perform(post("/api/sales")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [{"name": "Кирпич", "quantity": 1, "unitPrice": 100}],
                      "comment": "  Позвонить перед выдачей  "
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        long commentedSaleId = objectMapper.readTree(
            commentedSale.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/journal/{id}", commentedSaleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comment").value("Позвонить перед выдачей"));

        MvcResult blankCommentSale = mockMvc.perform(post("/api/sales")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [{"name": "Песок", "quantity": 1, "unitPrice": 50}],
                      "comment": "   "
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        long blankCommentSaleId = objectMapper.readTree(
            blankCommentSale.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/journal/{id}", blankCommentSaleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comment").value(nullValue()));
    }
}
