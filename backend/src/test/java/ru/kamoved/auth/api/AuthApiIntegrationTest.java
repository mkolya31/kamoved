package ru.kamoved.auth.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ru.kamoved.auth.application.AuthSessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

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
    private ObjectMapper objectMapper;

    @Value("${server.servlet.session.timeout}")
    private Duration sessionTimeout;

    @Value("${server.servlet.session.cookie.max-age}")
    private Duration sessionCookieMaxAge;

    @Value("${server.servlet.session.cookie.http-only}")
    private boolean sessionCookieHttpOnly;

    @Value("${server.servlet.session.cookie.secure}")
    private boolean sessionCookieSecure;

    @Value("${server.servlet.session.cookie.same-site}")
    private String sessionCookieSameSite;

    @Test
    void createsSessionAndReturnsCurrentUser() throws Exception {
        Instant loginStartedAt = Instant.now();
        LoginSession loginSession = loginAsAdmin();
        Instant loginFinishedAt = Instant.now();
        MockHttpSession session = loginSession.session();

        assertThat(session.getId()).isNotEqualTo(loginSession.csrfSessionId());
        assertThat(session.getMaxInactiveInterval())
            .isEqualTo(Math.toIntExact(AuthSessionService.MAX_SESSION_LIFETIME.toSeconds()));
        assertThat(session.getAttribute(AuthSessionService.EXPIRES_AT_ATTRIBUTE))
            .isInstanceOfSatisfying(Instant.class, expiresAt -> {
                assertThat(expiresAt)
                    .isAfterOrEqualTo(loginStartedAt.plus(AuthSessionService.MAX_SESSION_LIFETIME));
                assertThat(expiresAt)
                    .isBeforeOrEqualTo(loginFinishedAt.plus(AuthSessionService.MAX_SESSION_LIFETIME));
            });

        mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void expiresAuthenticatedSessionAfterAbsoluteDeadline() throws Exception {
        MockHttpSession session = loginAsAdmin().session();
        session.setAttribute(AuthSessionService.EXPIRES_AT_ATTRIBUTE, Instant.now().minusSeconds(1));

        MvcResult expired = mockMvc.perform(get("/api/auth/me").session(session))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertThat(session.isInvalid()).isTrue();
        assertClearedProtectedSessionCookie(expired);
    }

    @Test
    void logoutImmediatelyInvalidatesSessionAndClearsCookie() throws Exception {
        MockHttpSession session = loginAsAdmin().session();

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                .session(session)
                .with(csrf()))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(session.isInvalid()).isTrue();
        assertClearedProtectedSessionCookie(logout);
    }

    @Test
    void configuresPersistentProtectedSessionCookieForSevenDays() {
        assertThat(sessionTimeout).isEqualTo(Duration.ofDays(7));
        assertThat(sessionCookieMaxAge).isEqualTo(Duration.ofDays(7));
        assertThat(sessionCookieHttpOnly).isTrue();
        assertThat(sessionCookieSecure).isTrue();
        assertThat(sessionCookieSameSite).isEqualToIgnoringCase("lax");
    }

    private LoginSession loginAsAdmin() throws Exception {
        MockHttpSession csrfSession = new MockHttpSession();
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf").session(csrfSession))
            .andExpect(status().isOk())
            .andReturn();
        Map<String, String> csrfBody = objectMapper.readValue(
            csrf.getResponse().getContentAsByteArray(),
            new TypeReference<>() {
            }
        );
        String csrfSessionId = csrfSession.getId();

        var loginRequest = post("/api/auth/login")
                .session(csrfSession)
                .header(csrfBody.get("headerName"), csrfBody.get("token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "username": "admin",
                      "password": "test-password"
                    }
                    """);
        if (csrf.getResponse().getCookie("XSRF-TOKEN") != null) {
            loginRequest.cookie(csrf.getResponse().getCookie("XSRF-TOKEN"));
        }

        MvcResult login = mockMvc.perform(loginRequest)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.displayName").value("Тестовый пользователь"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        return new LoginSession(session, csrfSessionId);
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

    private record LoginSession(MockHttpSession session, String csrfSessionId) {
    }
}
