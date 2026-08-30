package ru.kamoved.notification.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "kamoved.users[0].username=active-subscriber",
    "kamoved.users[0].password=active-password",
    "kamoved.users[0].display-name=Активный подписчик",
    "kamoved.users[0].email=active-subscriber@example.test",
    "kamoved.users[0].active=true",
    "kamoved.users[0].notifications=TEST_EVENT",
    "kamoved.users[1].username=inactive-subscriber",
    "kamoved.users[1].password=inactive-password",
    "kamoved.users[1].display-name=Отключённый подписчик",
    "kamoved.users[1].email=inactive-subscriber@example.test",
    "kamoved.users[1].active=false",
    "kamoved.users[1].notifications=TEST_EVENT",
    "kamoved.users[2].username=shared-email-subscriber",
    "kamoved.users[2].password=shared-password",
    "kamoved.users[2].display-name=Подписчик с общей почтой",
    "kamoved.users[2].email=ACTIVE-SUBSCRIBER@example.test",
    "kamoved.users[2].active=true",
    "kamoved.users[2].notifications=TEST_EVENT",
    "kamoved.users[3].username=active-without-subscription",
    "kamoved.users[3].password=another-password",
    "kamoved.users[3].display-name=Активный без подписки",
    "kamoved.users[3].email=active-without-subscription@example.test",
    "kamoved.users[3].active=true"
})
@Import(EmailNotificationRecipientsIntegrationTest.NotificationTypesConfiguration.class)
@Transactional
class EmailNotificationRecipientsIntegrationTest {

    @Autowired
    private EmailNotificationRecipients recipients;

    @Autowired
    @Qualifier("testEvent")
    private EmailNotificationType testEvent;

    @Autowired
    @Qualifier("eventWithoutSubscribers")
    private EmailNotificationType eventWithoutSubscribers;

    @Test
    void selectsOnlyActiveSubscribedUsersAndDeduplicatesSharedEmail() {
        assertThat(recipients.findActiveRecipients(testEvent))
            .extracting(user -> user.getUsername())
            .containsExactly("active-subscriber");

        assertThat(recipients.findActiveRecipients(eventWithoutSubscribers)).isEmpty();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NotificationTypesConfiguration {

        @Bean
        EmailNotificationType testEvent() {
            return () -> "TEST_EVENT";
        }

        @Bean
        EmailNotificationType eventWithoutSubscribers() {
            return () -> "EVENT_WITHOUT_SUBSCRIBERS";
        }
    }
}
