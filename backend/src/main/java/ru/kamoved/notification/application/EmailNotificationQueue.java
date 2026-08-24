package ru.kamoved.notification.application;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.notification.config.NotificationProperties;
import ru.kamoved.notification.domain.EmailNotification;
import ru.kamoved.notification.domain.EmailNotificationStatus;
import ru.kamoved.notification.persistence.EmailNotificationRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class EmailNotificationQueue {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final EmailNotificationRepository notifications;
    private final NotificationProperties properties;

    public EmailNotificationQueue(
        EmailNotificationRepository notifications,
        NotificationProperties properties
    ) {
        this.notifications = notifications;
        this.properties = properties;
    }

    @Transactional
    public Optional<ClaimedEmailNotification> claimNext(OffsetDateTime now) {
        OffsetDateTime staleBefore = now.minus(properties.processingTimeout());
        return notifications.findDueForUpdate(
                EmailNotificationStatus.PENDING,
                EmailNotificationStatus.PROCESSING,
                now,
                staleBefore,
                PageRequest.of(0, 1)
            )
            .stream()
            .findFirst()
            .map(notification -> claim(notification, now));
    }

    private ClaimedEmailNotification claim(
        EmailNotification notification,
        OffsetDateTime now
    ) {
        notification.startProcessing(now);
        var recipient = notification.getRecipient();
        return new ClaimedEmailNotification(
            notification.getId(),
            notification.getNotificationKey(),
            recipient.getUsername(),
            recipient.getDisplayName(),
            recipient.getEmail(),
            notification.getSubject(),
            notification.getTextBody(),
            notification.getHtmlBody(),
            notification.getAttemptCount()
        );
    }

    @Transactional
    public void markSent(long id, OffsetDateTime now) {
        findForUpdate(id).markSent(now);
    }

    @Transactional
    public void scheduleRetry(
        ClaimedEmailNotification notification,
        Exception exception,
        OffsetDateTime now
    ) {
        Duration delay = retryDelay(notification.attemptCount());
        findForUpdate(notification.id()).scheduleRetry(
            now.plus(delay),
            errorMessage(exception),
            now
        );
    }

    private EmailNotification findForUpdate(long id) {
        return notifications.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalStateException("Уведомление " + id + " не найдено"));
    }

    private Duration retryDelay(int attemptCount) {
        Duration delay = properties.initialRetryDelay();
        for (int attempt = 1; attempt < attemptCount; attempt++) {
            if (delay.compareTo(properties.maxRetryDelay()) >= 0) {
                return properties.maxRetryDelay();
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(properties.maxRetryDelay()) > 0
            ? properties.maxRetryDelay()
            : delay;
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getClass().getSimpleName();
        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            message += ": " + exception.getMessage();
        }
        return message.length() <= MAX_ERROR_LENGTH
            ? message
            : message.substring(0, MAX_ERROR_LENGTH);
    }
}
