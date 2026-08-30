package ru.kamoved.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kamoved.bootstrap-admin")
public record BootstrapAdminProperties(
    String username,
    String password,
    String displayName,
    String email
) {
}
