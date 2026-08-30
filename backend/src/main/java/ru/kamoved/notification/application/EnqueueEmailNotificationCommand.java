package ru.kamoved.notification.application;

import java.time.OffsetDateTime;

public record EnqueueEmailNotificationCommand(
    String notificationKey,
    String recipientUsername,
    OffsetDateTime scheduledAt,
    String subject,
    String textBody,
    String htmlBody
) {
    public EnqueueEmailNotificationCommand {
        if (notificationKey == null || notificationKey.isBlank() || notificationKey.length() > 255) {
            throw new IllegalArgumentException("Ключ уведомления должен содержать от 1 до 255 символов");
        }
        if (recipientUsername == null || recipientUsername.isBlank()
            || recipientUsername.trim().length() > 100) {
            throw new IllegalArgumentException(
                "Логин получателя уведомления должен содержать от 1 до 100 символов");
        }
        if (scheduledAt == null) {
            throw new IllegalArgumentException("Время отправки уведомления должно быть указано");
        }
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new IllegalArgumentException("Тема уведомления должна содержать от 1 до 255 символов");
        }
        if (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Тема уведомления не должна содержать переносы строк");
        }
        if (textBody == null || textBody.isBlank()) {
            throw new IllegalArgumentException("Текст уведомления должен быть указан");
        }
    }
}
