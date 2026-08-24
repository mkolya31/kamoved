package ru.kamoved.notification.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;
import ru.kamoved.notification.domain.EmailNotification;
import ru.kamoved.notification.persistence.EmailNotificationRepository;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class EmailNotificationService {

    private final EmailNotificationRepository notifications;
    private final AppUserRepository users;
    private final Clock clock;

    public EmailNotificationService(
        EmailNotificationRepository notifications,
        AppUserRepository users,
        Clock clock
    ) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    public EnqueueResult enqueue(EnqueueEmailNotificationCommand command) {
        if (notifications.existsByNotificationKey(command.notificationKey())) {
            return EnqueueResult.ALREADY_ENQUEUED;
        }

        AppUser recipient = users.findByUsernameIgnoreCase(command.recipientUsername().trim())
            .orElseThrow(() -> new IllegalArgumentException(
                "Пользователь-получатель не найден: " + command.recipientUsername().trim()));
        if (!recipient.isActive()) {
            throw new IllegalStateException(
                "Пользователь-получатель отключен: " + recipient.getUsername());
        }
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            throw new IllegalStateException(
                "У пользователя-получателя не настроен email: " + recipient.getUsername());
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        EmailNotification notification = EmailNotification.pending(
            command.notificationKey(),
            recipient,
            command.subject(),
            command.textBody(),
            command.htmlBody(),
            command.scheduledAt(),
            now
        );

        try {
            notifications.saveAndFlush(notification);
            return EnqueueResult.ENQUEUED;
        } catch (DataIntegrityViolationException exception) {
            if (notifications.existsByNotificationKey(command.notificationKey())) {
                return EnqueueResult.ALREADY_ENQUEUED;
            }
            throw exception;
        }
    }
}
