package ru.kamoved.auth.application;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BootstrapUsersService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public BootstrapUsersService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void synchronize(List<ConfiguredUser> configuredUsers) {
        validate(configuredUsers);

        configuredUsers.forEach(configured -> {
            String username = configured.username().trim();
            String displayName = configured.displayName().trim();
            String email = normalizeEmail(configured.email());

            users.findByUsernameIgnoreCase(username).ifPresentOrElse(existing -> {
                String passwordHash = passwordEncoder.matches(
                    configured.password(), existing.getPasswordHash())
                    ? existing.getPasswordHash()
                    : passwordEncoder.encode(configured.password());

                existing.synchronizeFromConfiguration(
                    passwordHash,
                    displayName,
                    email,
                    configured.effectiveActive()
                );
                users.save(existing);
            }, () -> users.save(new AppUser(
                username,
                passwordEncoder.encode(configured.password()),
                displayName,
                email,
                configured.effectiveActive()
            )));
        });
    }

    private void validate(List<ConfiguredUser> configuredUsers) {
        if (configuredUsers.isEmpty()) {
            throw new IllegalStateException("Должен быть настроен хотя бы один пользователь Камоведа");
        }

        Set<String> usernames = new HashSet<>();
        Set<String> emails = new HashSet<>();
        for (ConfiguredUser configured : configuredUsers) {
            if (configured.username() == null || configured.username().isBlank()) {
                throw new IllegalStateException("Логин пользователя Камоведа не может быть пустым");
            }
            if (configured.username().trim().length() > 100) {
                throw new IllegalStateException("Логин пользователя Камоведа не может быть длиннее 100 символов");
            }
            if (configured.password() == null || configured.password().isBlank()) {
                throw new IllegalStateException(
                    "Пароль пользователя " + configured.username().trim() + " не может быть пустым");
            }
            if (configured.displayName() == null || configured.displayName().isBlank()) {
                throw new IllegalStateException(
                    "Имя пользователя " + configured.username().trim() + " не может быть пустым");
            }
            if (configured.displayName().trim().length() > 255) {
                throw new IllegalStateException(
                    "Имя пользователя " + configured.username().trim() + " не может быть длиннее 255 символов");
            }
            validateEmail(configured.email(), configured.username().trim());

            String normalizedUsername = configured.username().trim().toLowerCase(Locale.ROOT);
            if (!usernames.add(normalizedUsername)) {
                throw new IllegalStateException(
                    "Логин пользователя Камоведа указан несколько раз: " + configured.username().trim());
            }
            String normalizedEmail = normalizeEmail(configured.email());
            if (!emails.add(normalizedEmail)) {
                throw new IllegalStateException(
                    "Email пользователя Камоведа указан несколько раз: " + normalizedEmail);
            }
        }
    }

    private static void validateEmail(String value, String username) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Email пользователя " + username + " не может быть пустым");
        }
        if (value.trim().length() > 320) {
            throw new IllegalStateException(
                "Email пользователя " + username + " не может быть длиннее 320 символов");
        }
        try {
            InternetAddress address = new InternetAddress(value.trim(), true);
            address.validate();
        } catch (AddressException exception) {
            throw new IllegalStateException(
                "Email пользователя " + username + " имеет некорректный формат", exception);
        }
    }

    private static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
