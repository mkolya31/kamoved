package ru.kamoved.notification.infrastructure;

public class EmailNotificationSendException extends RuntimeException {

    public EmailNotificationSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
