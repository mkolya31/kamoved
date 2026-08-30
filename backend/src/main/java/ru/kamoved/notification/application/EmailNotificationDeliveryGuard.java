package ru.kamoved.notification.application;

public interface EmailNotificationDeliveryGuard {

    boolean supports(String notificationKey);

    boolean shouldDeliver(String notificationKey);
}
