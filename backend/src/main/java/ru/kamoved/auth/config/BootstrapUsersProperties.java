package ru.kamoved.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "kamoved")
public record BootstrapUsersProperties(
    List<ConfiguredUser> users
) {
    public BootstrapUsersProperties {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public record ConfiguredUser(
        String username,
        String password,
        String displayName,
        String email,
        Boolean active,
        String notifications
    ) {
        public boolean effectiveActive() {
            return active == null || active;
        }

        public Set<String> notificationTypes() {
            if (notifications == null || notifications.isBlank()) {
                return Set.of();
            }

            Set<String> types = new LinkedHashSet<>();
            for (String value : notifications.split(",")) {
                String type = value.trim();
                if (!type.isEmpty()) {
                    types.add(type);
                }
            }
            return Collections.unmodifiableSet(types);
        }
    }
}
