package ru.kamoved.notification.application;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class EmailNotificationTypeRegistry {

    private static final Pattern TYPE_CODE = Pattern.compile("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*");

    private final Map<String, EmailNotificationType> typesByCode;

    public EmailNotificationTypeRegistry(List<EmailNotificationType> types) {
        Map<String, EmailNotificationType> registeredTypes = new LinkedHashMap<>();
        for (EmailNotificationType type : types) {
            String code = type.code();
            if (code == null || !TYPE_CODE.matcher(code).matches()) {
                throw new IllegalStateException(
                    "Код типа email-уведомления должен быть в формате UPPER_SNAKE_CASE: " + code);
            }
            if (registeredTypes.putIfAbsent(code, type) != null) {
                throw new IllegalStateException(
                    "Тип email-уведомления зарегистрирован несколько раз: " + code);
            }
        }
        this.typesByCode = Map.copyOf(registeredTypes);
    }

    public Optional<EmailNotificationType> find(String code) {
        return Optional.ofNullable(typesByCode.get(code));
    }

    public EmailNotificationType requireRegistered(EmailNotificationType type) {
        if (type == null) {
            throw new IllegalArgumentException("Тип email-уведомления должен быть указан");
        }
        String code = type.code();
        if (code == null) {
            throw new IllegalArgumentException("Код типа email-уведомления должен быть указан");
        }
        return find(code).orElseThrow(() -> new IllegalArgumentException(
            "Тип email-уведомления не зарегистрирован: " + code));
    }
}
