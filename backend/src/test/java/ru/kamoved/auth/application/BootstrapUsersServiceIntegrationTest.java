package ru.kamoved.auth.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;
import ru.kamoved.auth.domain.AppUser;
import ru.kamoved.auth.persistence.AppUserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BootstrapUsersServiceIntegrationTest {

    @Autowired
    private BootstrapUsersService bootstrapUsersService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsUserAndUpdatesPasswordDisplayNameAndActiveFlag() {
        bootstrapUsersService.synchronize(List.of(
            new ConfiguredUser("seller", "first-password", "Первое имя", true)
        ));

        AppUser created = users.findByUsernameIgnoreCase("SELLER").orElseThrow();
        assertThat(created.getDisplayName()).isEqualTo("Первое имя");
        assertThat(created.isActive()).isTrue();
        assertThat(passwordEncoder.matches("first-password", created.getPasswordHash())).isTrue();

        bootstrapUsersService.synchronize(List.of(
            new ConfiguredUser("SELLER", "second-password", "Новое имя", false)
        ));

        AppUser updated = users.findByUsernameIgnoreCase("seller").orElseThrow();
        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getDisplayName()).isEqualTo("Новое имя");
        assertThat(updated.isActive()).isFalse();
        assertThat(passwordEncoder.matches("second-password", updated.getPasswordHash())).isTrue();
        assertThat(users.count()).isEqualTo(3);

        bootstrapUsersService.synchronize(List.of(
            new ConfiguredUser("another", "another-password", "Другой", true)
        ));

        assertThat(users.findByUsernameIgnoreCase("seller")).isPresent();
    }

    @Test
    void rejectsDuplicateUsernamesIgnoringCase() {
        List<ConfiguredUser> duplicates = List.of(
            new ConfiguredUser("seller", "first-password", "Первый", true),
            new ConfiguredUser("SELLER", "second-password", "Второй", true)
        );

        assertThatThrownBy(() -> bootstrapUsersService.synchronize(duplicates))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("указан несколько раз");
    }
}
