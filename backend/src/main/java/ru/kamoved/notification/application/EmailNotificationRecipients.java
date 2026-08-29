package ru.kamoved.notification.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class EmailNotificationRecipients {

    private final EmailNotificationSubscriptions subscriptions;
    private final AppUserRepository users;

    public EmailNotificationRecipients(
        EmailNotificationSubscriptions subscriptions,
        AppUserRepository users
    ) {
        this.subscriptions = subscriptions;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<AppUser> findActiveRecipients(EmailNotificationType type) {
        Set<String> subscribedUsernames = subscriptions.subscribedUsernames(type);
        if (subscribedUsernames.isEmpty()) {
            return List.of();
        }

        return users.findAllByActiveTrueOrderByIdAsc().stream()
            .filter(user -> subscribedUsernames.contains(normalizeUsername(user.getUsername())))
            .toList();
    }

    private static String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
