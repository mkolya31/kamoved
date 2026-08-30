package ru.kamoved.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNotificationAvailabilityTest {

    @Test
    void keepsDispatcherUnavailableWithoutFailingOnInvalidConfiguration() {
        NotificationProperties notifications = properties(true, "wrong-from");
        MailProperties mail = new MailProperties();
        mail.setHost("smtp.timeweb.ru");
        mail.setUsername("info@kamoved.ru");

        EmailNotificationAvailability availability =
            new EmailNotificationAvailability(notifications, mail);

        assertThat(availability.isAvailable()).isFalse();
    }

    @Test
    void enablesDispatcherForValidConfiguration() {
        NotificationProperties notifications = properties(true, "info@kamoved.ru");
        MailProperties mail = new MailProperties();
        mail.setHost("smtp.timeweb.ru");
        mail.setUsername("info@kamoved.ru");
        mail.setPassword("secret");

        EmailNotificationAvailability availability =
            new EmailNotificationAvailability(notifications, mail);

        assertThat(availability.isAvailable()).isTrue();
    }

    private static NotificationProperties properties(boolean enabled, String from) {
        return new NotificationProperties(
            enabled,
            from,
            "Камовед",
            null,
            30_000,
            20,
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            Duration.ofHours(6)
        );
    }
}
