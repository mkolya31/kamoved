package ru.kamoved.journal.application;

import org.springframework.stereotype.Component;
import ru.kamoved.notification.application.EmailNotificationType;

@Component
public class FactoryReadyEmailNotificationType implements EmailNotificationType {

    public static final String CODE = "FACTORY_READY";

    @Override
    public String code() {
        return CODE;
    }
}
