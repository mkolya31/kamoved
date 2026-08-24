package ru.kamoved.notification.application;

public record ClaimedEmailNotification(
    long id,
    String notificationKey,
    String recipientUsername,
    String recipientDisplayName,
    String recipientEmail,
    String subject,
    String textBody,
    String htmlBody,
    int attemptCount
) {
}
