package ru.kamoved.notification.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

    @Bean
    ApplicationRunner validateNotificationConfiguration(
        NotificationProperties notifications,
        MailProperties mail
    ) {
        return arguments -> {
            if (!notifications.enabled()) {
                return;
            }

            requireText(mail.getHost(), "KAMOVED_MAIL_HOST");
            requireText(mail.getUsername(), "KAMOVED_MAIL_USERNAME");
            requireText(mail.getPassword(), "KAMOVED_MAIL_PASSWORD");
            validateAddress(notifications.from(), "KAMOVED_MAIL_FROM");
            if (notifications.redirectsAllMail()) {
                validateAddress(notifications.redirectTo(), "KAMOVED_MAIL_REDIRECT_TO");
            }
            if (!notifications.from().equalsIgnoreCase(mail.getUsername())) {
                throw new IllegalStateException(
                    "KAMOVED_MAIL_FROM должен совпадать с KAMOVED_MAIL_USERNAME для Timeweb SMTP"
                );
            }
        };
    }

    private static void requireText(String value, String variable) {
        if (!NotificationProperties.hasText(value)) {
            throw new IllegalStateException(variable + " должен быть задан при включенной почте");
        }
    }

    private static void validateAddress(String value, String variable) {
        requireText(value, variable);
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
        } catch (AddressException exception) {
            throw new IllegalStateException(variable + " содержит некорректный email", exception);
        }
    }
}
