package ru.kamoved.journal.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.journal.persistence.JournalEntryRepository;
import ru.kamoved.notification.application.EmailNotificationDeliveryGuard;

import java.time.LocalDate;

@Component
public class FactoryReadyEmailDeliveryGuard implements EmailNotificationDeliveryGuard {

    private static final String PREFIX = FactoryReadyEmailNotificationType.CODE + ":";
    private final JournalEntryRepository entries;

    public FactoryReadyEmailDeliveryGuard(JournalEntryRepository entries) {
        this.entries = entries;
    }

    @Override
    public boolean supports(String notificationKey) {
        return notificationKey.startsWith(PREFIX);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean shouldDeliver(String notificationKey) {
        String[] parts = notificationKey.split(":", 5);
        if (parts.length != 5) return false;
        try {
            long orderId = Long.parseLong(parts[1]);
            LocalDate readyDate = LocalDate.parse(parts[2]);
            return entries.findById(orderId)
                .filter(order -> order.isFactoryReadyAttention())
                .filter(order -> readyDate.equals(order.getFactoryReadyDate()))
                .isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
