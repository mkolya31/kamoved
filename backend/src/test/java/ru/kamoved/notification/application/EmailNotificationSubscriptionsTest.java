package ru.kamoved.notification.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.kamoved.auth.config.BootstrapUsersProperties;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class EmailNotificationSubscriptionsTest {

    private static final EmailNotificationType KNOWN_TYPE = () -> "KNOWN_TYPE";

    @Test
    void keepsKnownSubscriptionsAndWarnsAboutUnknownTypes(CapturedOutput output) {
        BootstrapUsersProperties properties = new BootstrapUsersProperties(List.of(
            new ConfiguredUser(
                " Admin ",
                "password",
                "Администратор",
                "admin@example.test",
                true,
                "KNOWN_TYPE, UNKNOWN_TYPE, UNKNOWN_TYPE, known_type"
            )
        ));
        EmailNotificationTypeRegistry registry = new EmailNotificationTypeRegistry(
            List.of(KNOWN_TYPE));

        EmailNotificationSubscriptions subscriptions = new EmailNotificationSubscriptions(
            properties,
            registry
        );

        assertThat(subscriptions.subscribedUsernames(KNOWN_TYPE)).containsExactly("admin");
        assertThat(output).contains("Admin");
        assertThat(output).contains("UNKNOWN_TYPE");
        assertThat(output).contains("known_type");
    }
}
