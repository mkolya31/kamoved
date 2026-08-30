package ru.kamoved.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "kamoved.notifications")
public record NotificationProperties(
    boolean enabled,
    String from,
    String fromName,
    String redirectTo,
    long dispatchDelayMs,
    int batchSize,
    Duration processingTimeout,
    Duration initialRetryDelay,
    Duration maxRetryDelay
) {
    public NotificationProperties {
        fromName = hasText(fromName) ? fromName.trim() : "Камовед";
        dispatchDelayMs = dispatchDelayMs > 0 ? dispatchDelayMs : 30_000;
        batchSize = batchSize > 0 ? batchSize : 20;
        processingTimeout = positiveOrDefault(processingTimeout, Duration.ofMinutes(10));
        initialRetryDelay = positiveOrDefault(initialRetryDelay, Duration.ofMinutes(1));
        maxRetryDelay = positiveOrDefault(maxRetryDelay, Duration.ofHours(6));
        if (maxRetryDelay.compareTo(initialRetryDelay) < 0) {
            maxRetryDelay = initialRetryDelay;
        }
    }

    public boolean redirectsAllMail() {
        return hasText(redirectTo);
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value != null && !value.isZero() && !value.isNegative() ? value : fallback;
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
