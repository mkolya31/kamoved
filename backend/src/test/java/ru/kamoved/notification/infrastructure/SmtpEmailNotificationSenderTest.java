package ru.kamoved.notification.infrastructure;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import ru.kamoved.notification.application.ClaimedEmailNotification;
import ru.kamoved.notification.config.NotificationProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpEmailNotificationSenderTest {

    @Test
    void redirectsMailAndKeepsOriginalRecipientVisible() throws Exception {
        CapturingMailSender mailSender = new CapturingMailSender();
        NotificationProperties properties = new NotificationProperties(
            true,
            "info@kamoved.ru",
            "Камовед",
            "test@kamoved.ru",
            30_000,
            20,
            Duration.ofMinutes(10),
            Duration.ofMinutes(1),
            Duration.ofHours(6)
        );
        SmtpEmailNotificationSender sender = new SmtpEmailNotificationSender(
            mailSender,
            properties
        );

        sender.send(new ClaimedEmailNotification(
            1,
            "test-key",
            "admin",
            "Администратор",
            "admin@example.test",
            "Нужно проверить заказ",
            "Текст",
            "<p>Текст</p>",
            1
        ));

        MimeMessage message = mailSender.message;
        assertThat(message.getAllRecipients()).extracting(Object::toString)
            .containsExactly("test@kamoved.ru");
        assertThat(message.getSubject()).isEqualTo("[TEST → admin] Нужно проверить заказ");
        assertThat(message.getHeader("X-Kamoved-Notification-Key", null)).isEqualTo("test-key");
        assertThat(message.getHeader("X-Kamoved-Original-Recipient", null))
            .isEqualTo("admin@example.test");
    }

    private static final class CapturingMailSender extends JavaMailSenderImpl {

        private MimeMessage message;

        @Override
        public void send(MimeMessage mimeMessage) throws MailException {
            this.message = mimeMessage;
        }
    }
}
