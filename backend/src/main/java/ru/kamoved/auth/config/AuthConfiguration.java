package ru.kamoved.auth.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import ru.kamoved.auth.application.AuthSessionService;
import ru.kamoved.auth.application.BootstrapUsersService;
import ru.kamoved.auth.config.BootstrapUsersProperties.ConfiguredUser;

import java.util.List;

@Configuration
@EnableConfigurationProperties({BootstrapAdminProperties.class, BootstrapUsersProperties.class})
public class AuthConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        AuthSessionService authSessionService
    ) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    response.sendError(401)))
            .addFilterBefore(
                new AbsoluteSessionExpirationFilter(authSessionService),
                CsrfFilter.class
            );

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    ApplicationRunner bootstrapUsers(
        BootstrapUsersService bootstrapUsersService,
        BootstrapUsersProperties usersProperties,
        BootstrapAdminProperties legacyAdminProperties
    ) {
        List<ConfiguredUser> configuredUsers = usersProperties.users().isEmpty()
            ? List.of(new ConfiguredUser(
                legacyAdminProperties.username(),
                legacyAdminProperties.password(),
                legacyAdminProperties.displayName(),
                legacyAdminProperties.email(),
                true
            ))
            : usersProperties.users();

        return arguments -> bootstrapUsersService.synchronize(configuredUsers);
    }
}
