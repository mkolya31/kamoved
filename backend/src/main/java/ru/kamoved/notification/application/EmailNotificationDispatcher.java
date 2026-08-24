package ru.kamoved.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.kamoved.notification.config.NotificationProperties;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class EmailNotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationDispatcher.class);

    private final EmailNotificationQueue queue;
    private final EmailNotificationSender sender;
    private final NotificationProperties properties;
    private final Clock clock;

    public EmailNotificationDispatcher(
        EmailNotificationQueue queue,
        EmailNotificationSender sender,
        NotificationProperties properties,
        Clock clock
    ) {
        this.queue = queue;
        this.sender = sender;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(
        initialDelayString = "${kamoved.notifications.dispatch-delay-ms:30000}",
        fixedDelayString = "${kamoved.notifications.dispatch-delay-ms:30000}"
    )
    public void dispatchScheduled() {
        dispatchBatch();
    }

    public int dispatchBatch() {
        if (!properties.enabled()) {
            return 0;
        }

        int processed = 0;
        while (processed < properties.batchSize()) {
            OffsetDateTime now = OffsetDateTime.now(clock);
            Optional<ClaimedEmailNotification> claimed = queue.claimNext(now);
            if (claimed.isEmpty()) {
                break;
            }

            ClaimedEmailNotification notification = claimed.orElseThrow();
            try {
                sender.send(notification);
                queue.markSent(notification.id(), OffsetDateTime.now(clock));
            } catch (Exception exception) {
                log.warn(
                    "Не удалось отправить email-уведомление с ключом {} (попытка {})",
                    notification.notificationKey(),
                    notification.attemptCount(),
                    exception
                );
                queue.scheduleRetry(notification, exception, OffsetDateTime.now(clock));
            }
            processed++;
        }
        return processed;
    }
}
