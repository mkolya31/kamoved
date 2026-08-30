package ru.kamoved.notification.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import ru.kamoved.notification.application.ClaimedEmailNotification;
import ru.kamoved.notification.application.EmailNotificationSender;
import ru.kamoved.notification.config.NotificationProperties;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Component
public class SmtpEmailNotificationSender implements EmailNotificationSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailNotificationSender(
        JavaMailSender mailSender,
        NotificationProperties properties
    ) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(ClaimedEmailNotification notification) {
        String configuredRecipient = notification.recipientEmail();
        boolean redirected = properties.redirectsAllMail();
        String actualRecipient = redirected ? properties.redirectTo() : configuredRecipient;

        String subject = notification.subject();
        String textBody = notification.textBody();
        String htmlBody = notification.htmlBody();
        if (redirected) {
            String banner = "Тестовое перенаправление. Исходный получатель: "
                + notification.recipientDisplayName() + " ("
                + notification.recipientUsername() + ") <" + configuredRecipient + ">";
            subject = "[TEST → " + notification.recipientUsername() + "] " + subject;
            textBody = banner + "\n\n" + textBody;
            if (htmlBody != null) {
                htmlBody = "<p><strong>" + HtmlUtils.htmlEscape(banner) + "</strong></p>" + htmlBody;
            }
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message,
                true,
                StandardCharsets.UTF_8.name()
            );
            helper.setFrom(properties.from(), properties.fromName());
            helper.setTo(actualRecipient);
            helper.setSubject(subject);
            if (htmlBody == null) {
                helper.setText(textBody, false);
            } else {
                helper.setText(textBody, htmlBody);
            }
            message.setHeader("X-Kamoved-Notification-Key", notification.notificationKey());
            if (redirected) {
                message.setHeader("X-Kamoved-Original-Recipient", configuredRecipient);
            }
            mailSender.send(message);
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
            throw new EmailNotificationSendException("SMTP-сервер не принял письмо", exception);
        }
    }
}
