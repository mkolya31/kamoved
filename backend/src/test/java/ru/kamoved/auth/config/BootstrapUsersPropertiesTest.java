package ru.kamoved.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapUsersPropertiesTest {

    @Test
    void bindsIndexedEnvironmentVariables() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
            "configured-users-systemEnvironment",
            Map.of(
                "KAMOVED_USERS_0_USERNAME", "admin",
                "KAMOVED_USERS_0_PASSWORD", "admin-password",
                "KAMOVED_USERS_0_DISPLAY_NAME", "Николай",
                "KAMOVED_USERS_0_NOTIFICATIONS", " FIRST_TYPE, SECOND_TYPE, FIRST_TYPE,  ",
                "KAMOVED_USERS_1_USERNAME", "maksim",
                "KAMOVED_USERS_1_PASSWORD", "maksim-password",
                "KAMOVED_USERS_1_DISPLAY_NAME", "Максим",
                "KAMOVED_USERS_1_ACTIVE", "false",
                "KAMOVED_USERS_1_NOTIFICATIONS", "   "
            )
        ));
        ConfigurationPropertySources.attach(environment);

        BootstrapUsersProperties properties = Binder.get(environment)
            .bind("kamoved", Bindable.of(BootstrapUsersProperties.class))
            .orElseThrow(() -> new AssertionError("Настройки пользователей не связаны"));

        assertThat(properties.users()).hasSize(2);
        assertThat(properties.users().get(0).username()).isEqualTo("admin");
        assertThat(properties.users().get(0).effectiveActive()).isTrue();
        assertThat(properties.users().get(0).notificationTypes())
            .containsExactly("FIRST_TYPE", "SECOND_TYPE");
        assertThat(properties.users().get(1).username()).isEqualTo("maksim");
        assertThat(properties.users().get(1).displayName()).isEqualTo("Максим");
        assertThat(properties.users().get(1).effectiveActive()).isFalse();
        assertThat(properties.users().get(1).notificationTypes()).isEmpty();

        BootstrapUsersProperties.ConfiguredUser withoutNotifications =
            new BootstrapUsersProperties.ConfiguredUser(
                "third", "password", "Третий", "third@example.test", true, null);
        assertThat(withoutNotifications.notificationTypes()).isEmpty();
    }
}
