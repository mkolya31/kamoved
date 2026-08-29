package ru.kamoved.notification.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailNotificationTypeRegistryTest {

    @Test
    void rejectsNonCanonicalAndDuplicateTypeCodes() {
        assertThatThrownBy(() -> new EmailNotificationTypeRegistry(List.of(() -> "lower_case")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("UPPER_SNAKE_CASE");

        assertThatThrownBy(() -> new EmailNotificationTypeRegistry(List.of(
            () -> "SAME_TYPE",
            () -> "SAME_TYPE"
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("зарегистрирован несколько раз");
    }
}
