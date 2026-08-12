package ru.kamoved.journal.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SaleApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "admin")
    void createsCompletedPaidSaleAndShowsItFirstInJournal() throws Exception {
        mockMvc.perform(post("/api/sales")
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
            .andExpect(jsonPath("$.totalAmount").value(85935));

        mockMvc.perform(get("/api/journal"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].mainItem.name")
                .value("Готика Голд Кристалл"))
            .andExpect(jsonPath("$.items[0].totalAmount").value(85935));

        mockMvc.perform(get("/api/journal/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].name").value("Готика Голд Кристалл"))
            .andExpect(jsonPath("$.items[0].lineTotal").value(85935));

        mockMvc.perform(get("/api/journal?mode=active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty());
    }
}
