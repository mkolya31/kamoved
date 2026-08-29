package ru.kamoved.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.kamoved.auth.config.BootstrapUsersProperties;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class EmailNotificationSubscriptions {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSubscriptions.class);

    private final EmailNotificationTypeRegistry registry;
    private final Map<String, Set<String>> subscriberUsernamesByType;

    public EmailNotificationSubscriptions(
        BootstrapUsersProperties properties,
        EmailNotificationTypeRegistry registry
    ) {
        this.registry = registry;
        this.subscriberUsernamesByType = subscriptionsFrom(properties);
    }

    public Set<String> subscribedUsernames(EmailNotificationType type) {
        String code = registry.requireRegistered(type).code();
        return subscriberUsernamesByType.getOrDefault(code, Set.of());
    }

    private Map<String, Set<String>> subscriptionsFrom(BootstrapUsersProperties properties) {
        Map<String, Set<String>> subscriptions = new LinkedHashMap<>();
        for (ConfiguredUser user : properties.users()) {
            for (String configuredType : user.notificationTypes()) {
                registry.find(configuredType).ifPresentOrElse(
                    type -> addSubscription(subscriptions, type.code(), user.username()),
                    () -> log.warn(
                        "Пользователь {} подписан на неизвестный тип email-уведомления: {}",
                        usernameForLog(user),
                        configuredType
                    )
                );
            }
        }

        subscriptions.replaceAll((type, usernames) -> Set.copyOf(usernames));
        return Map.copyOf(subscriptions);
    }

    private static void addSubscription(
        Map<String, Set<String>> subscriptions,
        String type,
        String username
    ) {
        if (username == null || username.isBlank()) {
            return;
        }
        subscriptions.computeIfAbsent(type, ignored -> new LinkedHashSet<>())
            .add(normalizeUsername(username));
    }

    private static String usernameForLog(ConfiguredUser user) {
        return user.username() == null || user.username().isBlank()
            ? "<не задан>"
            : user.username().trim();
    }

    private static String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }
}
