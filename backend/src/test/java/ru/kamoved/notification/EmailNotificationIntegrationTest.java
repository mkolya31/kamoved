package ru.kamoved.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import ru.kamoved.auth.application.BootstrapUsersService;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;
import ru.kamoved.notification.application.ClaimedEmailNotification;
import ru.kamoved.notification.application.EmailNotificationDispatcher;
import ru.kamoved.notification.application.EmailNotificationQueue;
import ru.kamoved.notification.application.EmailNotificationSender;
import ru.kamoved.notification.application.EmailNotificationService;
import ru.kamoved.notification.application.EnqueueEmailNotificationCommand;
import ru.kamoved.notification.application.EnqueueResult;
import ru.kamoved.notification.domain.EmailNotification;
import ru.kamoved.notification.domain.EmailNotificationStatus;
import ru.kamoved.notification.persistence.EmailNotificationRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "kamoved.notifications.enabled=true",
    "kamoved.notifications.from=info@kamoved.ru",
    "kamoved.notifications.dispatch-delay-ms=3600000",
    "kamoved.notifications.processing-timeout=1m",
    "kamoved.notifications.initial-retry-delay=1m",
    "kamoved.notifications.max-retry-delay=5m",
    "spring.mail.username=info@kamoved.ru",
    "spring.mail.password=test-password"
})
@Import(EmailNotificationIntegrationTest.NotificationTestConfiguration.class)
class EmailNotificationIntegrationTest {

    private static final Instant INITIAL_TIME = Instant.parse("2026-08-24T06:00:00Z");

    @Autowired
    private EmailNotificationService service;

    @Autowired
    private BootstrapUsersService bootstrapUsersService;

    @Autowired
    private EmailNotificationDispatcher dispatcher;

    @Autowired
    private EmailNotificationQueue queue;

    @Autowired
    private EmailNotificationRepository notifications;

    @Autowired
    private FakeEmailNotificationSender sender;

    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        notifications.deleteAll();
        sender.reset();
        clock.set(INITIAL_TIME);
    }

    @AfterEach
    void restoreAdminEmail() {
        synchronizeAdminEmail("admin@example.test");
    }

    @Test
    void enqueuesEachNotificationKeyOnlyOnce() {
        EnqueueEmailNotificationCommand command = command("example:42:2026-08-30");

        assertThat(service.enqueue(command)).isEqualTo(EnqueueResult.ENQUEUED);
        assertThat(service.enqueue(command)).isEqualTo(EnqueueResult.ALREADY_ENQUEUED);

        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void sendsDueNotificationAndMarksItSent() {
        service.enqueue(command("due-now"));

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        EmailNotification notification = find("due-now");
        assertThat(notification.getStatus()).isEqualTo(EmailNotificationStatus.SENT);
        assertThat(notification.getAttemptCount()).isEqualTo(1);
        assertThat(notification.getSentAt()).isEqualTo(now());
        assertThat(sender.sentKeys()).containsExactly("due-now");
        assertThat(sender.sentRecipients()).containsExactly("admin@example.test");
    }

    @Test
    void usesCurrentUserEmailWhenQueuedNotificationIsSent() {
        service.enqueue(command("changed-recipient-email"));
        synchronizeAdminEmail("new-admin@example.test");

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        assertThat(sender.sentRecipients()).containsExactly("new-admin@example.test");
    }

    @Test
    void leavesFutureNotificationPending() {
        service.enqueue(new EnqueueEmailNotificationCommand(
            "future",
            "admin",
            now().plusHours(1),
            "Тема",
            "Текст",
            null
        ));

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(find("future").getStatus()).isEqualTo(EmailNotificationStatus.PENDING);
        assertThat(sender.sentKeys()).isEmpty();
    }

    @Test
    void retriesTemporaryFailureAfterBackoff() {
        sender.failNextAttempts(1);
        service.enqueue(command("retry"));

        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);
        EmailNotification failed = find("retry");
        assertThat(failed.getStatus()).isEqualTo(EmailNotificationStatus.PENDING);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isEqualTo(now().plusMinutes(1));
        assertThat(failed.getLastError()).contains("Временная ошибка SMTP");

        clock.advance(Duration.ofSeconds(59));
        assertThat(dispatcher.dispatchBatch()).isZero();

        clock.advance(Duration.ofSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);
        EmailNotification sent = find("retry");
        assertThat(sent.getStatus()).isEqualTo(EmailNotificationStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
        assertThat(sender.sentKeys()).containsExactly("retry");
    }

    @Test
    void recoversProcessingNotificationAfterTimeout() {
        service.enqueue(command("stale"));
        assertThat(queue.claimNext(now())).isPresent();
        assertThat(find("stale").getStatus()).isEqualTo(EmailNotificationStatus.PROCESSING);

        clock.advance(Duration.ofMinutes(1));
        assertThat(dispatcher.dispatchBatch()).isEqualTo(1);

        EmailNotification sent = find("stale");
        assertThat(sent.getStatus()).isEqualTo(EmailNotificationStatus.SENT);
        assertThat(sent.getAttemptCount()).isEqualTo(2);
    }

    private EnqueueEmailNotificationCommand command(String key) {
        return new EnqueueEmailNotificationCommand(
            key,
            "admin",
            now(),
            "Тема уведомления",
            "Текст уведомления",
            "<p>Текст уведомления</p>"
        );
    }

    private EmailNotification find(String key) {
        return notifications.findByNotificationKey(key).orElseThrow();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void synchronizeAdminEmail(String email) {
        bootstrapUsersService.synchronize(List.of(new ConfiguredUser(
            "admin",
            "test-password",
            "Тестовый пользователь",
            email,
            true
        )));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NotificationTestConfiguration {

        @Bean
        @Primary
        MutableClock notificationTestClock() {
            return new MutableClock(INITIAL_TIME);
        }

        @Bean
        @Primary
        FakeEmailNotificationSender fakeEmailNotificationSender() {
            return new FakeEmailNotificationSender();
        }
    }

    static final class FakeEmailNotificationSender implements EmailNotificationSender {

        private final List<String> sentKeys = new ArrayList<>();
        private final List<String> sentRecipients = new ArrayList<>();
        private int failuresRemaining;

        @Override
        public void send(ClaimedEmailNotification notification) {
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IllegalStateException("Временная ошибка SMTP");
            }
            sentKeys.add(notification.notificationKey());
            sentRecipients.add(notification.recipientEmail());
        }

        void failNextAttempts(int count) {
            failuresRemaining = count;
        }

        List<String> sentKeys() {
            return List.copyOf(sentKeys);
        }

        List<String> sentRecipients() {
            return List.copyOf(sentRecipients);
        }

        void reset() {
            sentKeys.clear();
            sentRecipients.clear();
            failuresRemaining = 0;
        }
    }

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
