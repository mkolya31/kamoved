package ru.kamoved.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

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
        Boolean active
    ) {
        public boolean effectiveActive() {
            return active == null || active;
        }
    }
}
