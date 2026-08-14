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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = {
    "DELETE FROM entry_contact",
    "DELETE FROM journal_entry_item",
    "DELETE FROM journal_entry"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @WithMockUser(username = "admin")
    void createsOrderWithContactsAndShowsItInJournalAndActiveOrders() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {
                          "name": "Готика Голд Кристалл 60 мм",
                          "quantity": 30.1,
                          "unit": "SQUARE_METER",
                          "unitPrice": 2855
                        },
                        {
                          "name": "Доставка манипулятором",
                          "quantity": 1,
                          "unit": "PIECE",
                          "unitPrice": 5000
                        }
                      ],
                      "client": {
                        "name": "Владимир",
                        "phone": "+7 (999) 123-45-67",
                        "comment": "Основной покупатель"
                      },
                      "additionalContacts": [
                        {
                          "name": "Нурик",
                          "phone": "8 (998) 998-98-98",
                          "comment": "Прораб, звонить по доставке"
                        }
                      ],
                      "paymentStatus": "PREPAID",
                      "prepaymentAmount": 40000,
                      "executionStatus": "IN_PRODUCTION",
                      "fulfillmentMethod": "DELIVERY",
                      "deliveryAddress": "СНТ Главножуково",
                      "comment": "Позвонить перед отправкой"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("ORDER"))
            .andExpect(jsonPath("$.paymentStatus").value("PREPAID"))
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"))
            .andExpect(jsonPath("$.totalAmount").value(90935))
            .andExpect(jsonPath("$.clientName").value("Владимир"))
            .andExpect(jsonPath("$.fulfillmentMethod").value("DELIVERY"))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        long orderId = created.get("id").asLong();

        mockMvc.perform(get("/api/journal"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(orderId))
            .andExpect(jsonPath("$.items[0].type").value("ORDER"));

        mockMvc.perform(get("/api/journal?mode=active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(orderId));

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.client.name").value("Владимир"))
            .andExpect(jsonPath("$.client.phone").value("+7 (999) 123-45-67"))
            .andExpect(jsonPath("$.additionalContacts[0].name").value("Нурик"))
            .andExpect(jsonPath("$.prepaymentAmount").value(40000))
            .andExpect(jsonPath("$.remainingAmount").value(50935))
            .andExpect(jsonPath("$.deliveryAddress").value("СНТ Главножуково"))
            .andExpect(jsonPath("$.comment").value("Позвонить перед отправкой"));

        assertThat(jdbc.queryForObject(
            "SELECT normalized_phone FROM entry_contact WHERE journal_entry_id = ? AND type = 'CLIENT'",
            String.class,
            orderId
        )).isEqualTo("79991234567");
    }

    @Test
    @WithMockUser(username = "admin")
    void rejectsDeliveryWithoutAddress() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("""
                    "fulfillmentMethod": "DELIVERY"
                    """)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Для доставки укажите адрес"));
    }

    @Test
    @WithMockUser(username = "admin")
    void rejectsInvalidPrepayment() throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("""
                    "paymentStatus": "PREPAID",
                    "prepaymentAmount": 1000
                    """)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Предоплата должна быть меньше суммы заказа"));
    }

    @Test
    @WithMockUser(username = "admin")
    void quicklyChangesExecutionStatusAndRejectsStaleVersion() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("\"executionStatus\": \"NEW\"")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.executionStatus").value("NEW"))
            .andExpect(jsonPath("$.version").value(0))
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(patch("/api/orders/{id}/execution-status", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "executionStatus": "COMPLETED",
                      "version": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/journal?mode=active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(patch("/api/orders/{id}/execution-status", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "executionStatus": "CANCELLED",
                      "version": 0
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                "Заказ уже изменён другим пользователем. Обновите журнал и повторите действие"));
    }

    private String minimalOrder(String additionalFields) {
        return """
            {
              "items": [
                {
                  "name": "Тестовый товар",
                  "quantity": 1,
                  "unitPrice": 1000
                }
              ],
              %s
            }
            """.formatted(additionalFields);
    }
}
