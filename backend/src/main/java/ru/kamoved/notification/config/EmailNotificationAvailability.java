package ru.kamoved.notification.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EmailNotificationAvailability {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAvailability.class);

    private final boolean available;

    public EmailNotificationAvailability(
        NotificationProperties notifications,
        MailProperties mail
    ) {
        Optional<String> configurationError = configurationError(notifications, mail);
        this.available = notifications.enabled() && configurationError.isEmpty();
        configurationError.ifPresent(error -> log.error(
            "Отправка email-уведомлений приостановлена: {}. Основное приложение продолжит работу",
            error
        ));
    }

    public boolean isAvailable() {
        return available;
    }

    private static Optional<String> configurationError(
        NotificationProperties notifications,
        MailProperties mail
    ) {
        if (!notifications.enabled()) {
            return Optional.empty();
        }
        if (!NotificationProperties.hasText(mail.getHost())) {
            return Optional.of("KAMOVED_MAIL_HOST не задан");
        }
        if (!NotificationProperties.hasText(mail.getUsername())) {
            return Optional.of("KAMOVED_MAIL_USERNAME не задан");
        }
        if (!NotificationProperties.hasText(mail.getPassword())) {
            return Optional.of("KAMOVED_MAIL_PASSWORD не задан");
        }
        Optional<String> fromError = invalidAddress(notifications.from(), "KAMOVED_MAIL_FROM");
        if (fromError.isPresent()) {
            return fromError;
        }
        if (notifications.redirectsAllMail()) {
            Optional<String> redirectError = invalidAddress(
                notifications.redirectTo(),
                "KAMOVED_MAIL_REDIRECT_TO"
            );
            if (redirectError.isPresent()) {
                return redirectError;
            }
        }
        if (!notifications.from().equalsIgnoreCase(mail.getUsername())) {
            return Optional.of(
                "KAMOVED_MAIL_FROM должен совпадать с KAMOVED_MAIL_USERNAME для Timeweb SMTP"
            );
        }
        return Optional.empty();
    }

    private static Optional<String> invalidAddress(String value, String variable) {
        if (!NotificationProperties.hasText(value)) {
            return Optional.of(variable + " не задан");
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return Optional.empty();
        } catch (AddressException exception) {
            return Optional.of(variable + " содержит некорректный email");
        }
    }
}
