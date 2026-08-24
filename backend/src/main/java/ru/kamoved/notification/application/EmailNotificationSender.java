package ru.kamoved.notification.application;

public interface EmailNotificationSender {

    void send(ClaimedEmailNotification notification);
}
