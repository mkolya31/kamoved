package ru.kamoved.auth.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.kamoved.auth.application.AuthSessionService;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcIndexedSessionRepository sessions;

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${spring.session.timeout}")
    private Duration springSessionTimeout;

    @Value("${spring.session.jdbc.initialize-schema}")
    private String sessionSchemaInitialization;

    @Value("${spring.session.jdbc.cleanup-cron}")
    private String sessionCleanupCron;

    @Value("${server.servlet.session.timeout}")
    private Duration servletSessionTimeout;

    @Value("${server.servlet.session.cookie.max-age}")
    private Duration sessionCookieMaxAge;

    @Value("${server.servlet.session.cookie.http-only}")
    private boolean sessionCookieHttpOnly;

    @Value("${server.servlet.session.cookie.secure}")
    private boolean sessionCookieSecure;

    @Value("${server.servlet.session.cookie.same-site}")
    private String sessionCookieSameSite;

    @BeforeEach
    void deletePersistedSessions() {
        jdbc.update("DELETE FROM spring_session");
    }

    @Test
    void createsPersistedSessionAndReturnsCurrentUser() throws Exception {
        Instant loginStartedAt = Instant.now();
        LoginSession loginSession = loginAsAdmin();
        Instant loginFinishedAt = Instant.now();

        Session persistedSession = sessions.findById(loginSession.repositoryId());
        assertThat(persistedSession).isNotNull();
        assertThat(persistedSession.getMaxInactiveInterval())
            .isEqualTo(AuthSessionService.MAX_SESSION_LIFETIME);
        Object expiresAtAttribute = persistedSession.getAttribute(
            AuthSessionService.EXPIRES_AT_ATTRIBUTE
        );
        assertThat(expiresAtAttribute)
            .isInstanceOfSatisfying(Instant.class, expiresAt -> {
                assertThat(expiresAt)
                    .isAfterOrEqualTo(loginStartedAt.plus(AuthSessionService.MAX_SESSION_LIFETIME));
                assertThat(expiresAt)
                    .isBeforeOrEqualTo(loginFinishedAt.plus(AuthSessionService.MAX_SESSION_LIFETIME));
            });

        mockMvc.perform(get("/api/auth/me").cookie(loginSession.cookie()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void expiresAuthenticatedSessionAfterAbsoluteDeadline() throws Exception {
        LoginSession loginSession = loginAsAdmin();
        Session persistedSession = sessions.findById(loginSession.repositoryId());
        persistedSession.setAttribute(
            AuthSessionService.EXPIRES_AT_ATTRIBUTE,
            Instant.now().minusSeconds(1)
        );
        save(persistedSession);

        MvcResult expired = mockMvc.perform(get("/api/auth/me").cookie(loginSession.cookie()))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(sessions.findById(loginSession.repositoryId())).isNull();
        assertClearedProtectedSessionCookie(expired);
    }

    @Test
    void rejectsIdleExpiredSessionAndRemovesItDuringCleanup() throws Exception {
        LoginSession loginSession = loginAsAdmin();
        long expiredAt = Instant.now().minus(AuthSessionService.MAX_SESSION_LIFETIME)
            .minusSeconds(1)
            .toEpochMilli();
        jdbc.update(
            """
                UPDATE spring_session
                SET last_access_time = ?, expiry_time = ?
                WHERE session_id = ?
                """,
            expiredAt,
            expiredAt,
            loginSession.repositoryId()
        );

        mockMvc.perform(get("/api/auth/me").cookie(loginSession.cookie()))
            .andExpect(status().isUnauthorized());

        assertThat(sessions.findById(loginSession.repositoryId())).isNull();
        sessions.cleanUpExpiredSessions();
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE session_id = ?",
            Long.class,
            loginSession.repositoryId()
        )).isZero();
    }

    @Test
    void logoutImmediatelyDeletesPersistedSessionAndClearsCookie() throws Exception {
        LoginSession loginSession = loginAsAdmin();

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                .cookie(loginSession.cookie())
                .with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(sessions.findById(loginSession.repositoryId())).isNull();
        assertClearedProtectedSessionCookie(logout);
    }

    @Test
    void treatsLegacyTomcatCookieAsUnknownSessionInsteadOfInvalidDatabaseText() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .cookie(new Cookie("JSESSIONID", "AAAA")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void configuresJdbcSessionsAndProtectedCookieForSevenDays() {
        assertThat(springSessionTimeout).isEqualTo(Duration.ofDays(7));
        assertThat(servletSessionTimeout).isEqualTo(Duration.ofDays(7));
        assertThat(sessionCookieMaxAge).isEqualTo(Duration.ofDays(7));
        assertThat(sessionSchemaInitialization).isEqualToIgnoringCase("never");
        assertThat(sessionCleanupCron).isEqualTo("0 * * * * *");
        assertThat(sessionCookieHttpOnly).isTrue();
        assertThat(sessionCookieSecure).isTrue();
        assertThat(sessionCookieSameSite).isEqualToIgnoringCase("lax");
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "wrong"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatesAdditionalConfiguredUser() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "maksim",
                      "password": "maxim-test-password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("maksim"))
            .andExpect(jsonPath("$.displayName").value("Максим"));
    }

    private LoginSession loginAsAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "test-password"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.displayName").value("Тестовый пользователь"))
            .andReturn();

        String repositoryId = jdbc.queryForObject(
            """
                SELECT session_id
                FROM spring_session
                WHERE principal_name = 'admin'
                ORDER BY creation_time DESC
                LIMIT 1
                """,
            String.class
        );

        return new LoginSession(sessionCookieFrom(login), repositoryId);
    }

    private Cookie sessionCookieFrom(MvcResult result) {
        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
            .filter(cookie -> cookie.startsWith("JSESSIONID="))
            .filter(cookie -> !cookie.contains("Max-Age=0"))
            .findFirst()
            .orElseThrow();
        int valueStart = "JSESSIONID=".length();
        int valueEnd = header.indexOf(';', valueStart);
        String value = valueEnd < 0 ? header.substring(valueStart) : header.substring(valueStart, valueEnd);
        assertThat(value).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        );
        return new Cookie("JSESSIONID", value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void save(Session session) {
        ((SessionRepository) sessions).save(session);
    }

    private void assertClearedProtectedSessionCookie(MvcResult result) {
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
            .anySatisfy(cookie -> assertThat(cookie)
                .contains("JSESSIONID=")
                .contains("Max-Age=0")
                .contains("Path=/")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Lax"));
    }

    private record LoginSession(Cookie cookie, String repositoryId) {
    }
}
