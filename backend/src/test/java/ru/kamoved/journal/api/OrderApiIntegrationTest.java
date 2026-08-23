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
    "DELETE FROM journal_payment",
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
                      "initialPayment": {
                        "amount": 40000,
                        "paymentMethod": "PERSONAL_TRANSFER",
                        "comment": "Перевод на карту"
                      },
                      "executionStatus": "IN_PRODUCTION",
                      "fulfillmentMethod": "DELIVERY_FACTORY",
                      "deliveryAddress": "СНТ Главножуково",
                      "comment": "Позвонить перед отправкой"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("ORDER"))
            .andExpect(jsonPath("$.paymentStatus").value("PREPAID"))
            .andExpect(jsonPath("$.prepaymentAmount").value(40000))
            .andExpect(jsonPath("$.remainingAmount").value(50935))
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"))
            .andExpect(jsonPath("$.totalAmount").value(90935))
            .andExpect(jsonPath("$.clientName").value("Владимир"))
            .andExpect(jsonPath("$.fulfillmentMethod").value("DELIVERY_FACTORY"))
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

    @ParameterizedTest
    @ValueSource(strings = {"DELIVERY_FACTORY", "DELIVERY_MARKET"})
    @WithMockUser(username = "admin")
    void rejectsDeliveryWithoutAddress(String fulfillmentMethod) throws Exception {
        mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("""
                    "fulfillmentMethod": "%s"
                    """.formatted(fulfillmentMethod))))
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
                    "initialPayment": {"amount": 1001, "paymentMethod": "CASH"}
                    """)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Сумма платежа не может превышать остаток заказа"));
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

    @Test
    @WithMockUser(username = "admin")
    void addsSeveralPaymentsAndDerivesStatusWithoutChangingExecutionStatus() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("\"executionStatus\": \"IN_PRODUCTION\"")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentStatus").value("UNPAID"))
            .andExpect(jsonPath("$.remainingAmount").value(1000))
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 400,
                      "paymentMethod": "CASH",
                      "comment": "В кассу"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentStatus").value("PREPAID"))
            .andExpect(jsonPath("$.prepaymentAmount").value(400))
            .andExpect(jsonPath("$.paidAmount").value(400))
            .andExpect(jsonPath("$.remainingAmount").value(600))
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"));

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 600,
                      "paymentMethod": "CARD"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.prepaymentAmount").isEmpty())
            .andExpect(jsonPath("$.paidAmount").value(1000))
            .andExpect(jsonPath("$.remainingAmount").value(0))
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"));

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.payments.length()").value(2))
            .andExpect(jsonPath("$.payments[0].paymentMethod").value("CASH"))
            .andExpect(jsonPath("$.payments[1].paymentMethod").value("CARD"))
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"));
    }

    @Test
    @WithMockUser(username = "admin")
    void validatesAndCorrectsPaymentWithoutDeletingHistory() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("\"executionStatus\": \"NEW\"")))
            .andExpect(status().isCreated())
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 1001,
                      "paymentMethod": "CASH"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Сумма платежа не может превышать остаток заказа"));

        MvcResult paymentResult = mockMvc.perform(post("/api/orders/{id}/payments", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 400,
                      "paymentMethod": "CASH"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        long paymentId = jdbc.queryForObject(
            "SELECT id FROM journal_payment WHERE journal_entry_id = ? AND voided_at IS NULL",
            Long.class,
            orderId
        );

        mockMvc.perform(post("/api/payments/{id}/corrections", paymentId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "amount": 300,
                      "paymentMethod": "BANK_ACCOUNT",
                      "comment": "Исправленные данные",
                      "reason": "Ошибочно указана сумма"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paidAmount").value(300))
            .andExpect(jsonPath("$.remainingAmount").value(700))
            .andExpect(jsonPath("$.payments.length()").value(2))
            .andExpect(jsonPath("$.payments[0].active").value(false))
            .andExpect(jsonPath("$.payments[1].active").value(true))
            .andExpect(jsonPath("$.payments[1].correctionOfId").value(paymentId))
            .andExpect(jsonPath("$.payments[1].correctionReason").value("Ошибочно указана сумма"));
    }

    @Test
    @WithMockUser(username = "admin")
    void fullyUpdatesOrderAndReplacesItemsAndContacts() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("""
                    "client": {"name": "Старый клиент", "phone": "+7 900 000-00-00"},
                    "additionalContacts": [{"name": "Старый контакт"}],
                    "initialPayment": {"amount": 1000, "paymentMethod": "CASH"},
                    "comment": "Старый комментарий"
                    """)))
            .andExpect(status().isCreated())
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(patch("/api/orders/{id}", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {
                          "name": "Обновлённый товар",
                          "quantity": 2,
                          "unit": "PACKAGE",
                          "unitPrice": 1500
                        },
                        {
                          "name": "Доставка",
                          "quantity": 1,
                          "unit": "PIECE",
                          "unitPrice": 500
                        }
                      ],
                      "client": {
                        "name": "Новый клиент",
                        "phone": "+7 (999) 111-22-33",
                        "comment": "Основной контакт"
                      },
                      "additionalContacts": [
                        {
                          "name": "Прораб",
                          "phone": "8 999 444-55-66",
                          "comment": "Звонить перед доставкой"
                        }
                      ],
                      "executionStatus": "READY_FACTORY",
                      "fulfillmentMethod": "DELIVERY_MARKET",
                      "deliveryAddress": "Новый адрес",
                      "comment": "Новый комментарий",
                      "version": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].name").value("Обновлённый товар"))
            .andExpect(jsonPath("$.items[0].unit").value("PACKAGE"))
            .andExpect(jsonPath("$.totalAmount").value(3500))
            .andExpect(jsonPath("$.paymentStatus").value("PREPAID"))
            .andExpect(jsonPath("$.prepaymentAmount").value(1000))
            .andExpect(jsonPath("$.remainingAmount").value(2500))
            .andExpect(jsonPath("$.executionStatus").value("READY_FACTORY"))
            .andExpect(jsonPath("$.client.name").value("Новый клиент"))
            .andExpect(jsonPath("$.additionalContacts.length()").value(1))
            .andExpect(jsonPath("$.additionalContacts[0].name").value("Прораб"))
            .andExpect(jsonPath("$.fulfillmentMethod").value("DELIVERY_MARKET"))
            .andExpect(jsonPath("$.deliveryAddress").value("Новый адрес"))
            .andExpect(jsonPath("$.comment").value("Новый комментарий"))
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.client.name").value("Новый клиент"))
            .andExpect(jsonPath("$.additionalContacts.length()").value(1))
            .andExpect(jsonPath("$.comment").value("Новый комментарий"));

        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM journal_entry_item WHERE journal_entry_id = ?",
            Integer.class,
            orderId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM entry_contact WHERE journal_entry_id = ?",
            Integer.class,
            orderId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "SELECT normalized_phone FROM entry_contact WHERE journal_entry_id = ? AND type = 'CLIENT'",
            String.class,
            orderId
        )).isEqualTo("79991112233");
    }

    @Test
    @WithMockUser(username = "admin")
    void rejectsFullUpdateAfterQuickStatusChangeWithStaleVersion() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("\"additionalContacts\": []")))
            .andExpect(status().isCreated())
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(patch("/api/orders/{id}/execution-status", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "executionStatus": "IN_PRODUCTION",
                      "version": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/api/orders/{id}", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullOrderUpdate(0, "Попытка перезаписать заказ")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(
                "Заказ уже изменён другим пользователем. Обновите журнал и повторите действие"));

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionStatus").value("IN_PRODUCTION"))
            .andExpect(jsonPath("$.comment").isEmpty())
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser(username = "admin")
    void validatesFullUpdateAndKeepsOrderUnchangedAfterRejection() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalOrder("\"additionalContacts\": []")))
            .andExpect(status().isCreated())
            .andReturn();

        long orderId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
            .get("id").asLong();

        mockMvc.perform(patch("/api/orders/{id}", orderId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "items": [
                        {
                          "name": "Изменённый товар",
                          "quantity": 1,
                          "unitPrice": 1000
                        }
                      ],
                      "additionalContacts": [],
                      "executionStatus": "NEW",
                      "fulfillmentMethod": "DELIVERY_MARKET",
                      "version": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Для доставки укажите адрес"));

        mockMvc.perform(get("/api/journal/{id}", orderId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Тестовый товар"))
            .andExpect(jsonPath("$.fulfillmentMethod").isEmpty())
            .andExpect(jsonPath("$.version").value(0));
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

    private String fullOrderUpdate(long version, String comment) {
        return """
            {
              "items": [
                {
                  "name": "Изменённый товар",
                  "quantity": 1,
                  "unitPrice": 1000
                }
              ],
              "additionalContacts": [],
              "executionStatus": "NEW",
              "comment": "%s",
              "version": %d
            }
            """.formatted(comment, version);
    }
}
