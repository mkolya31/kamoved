package ru.kamoved.auth.application;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthSessionService {

    public static final String EXPIRES_AT_ATTRIBUTE =
        AuthSessionService.class.getName() + ".EXPIRES_AT";
    public static final Duration MAX_SESSION_LIFETIME = Duration.ofDays(7);

    private final String sessionCookieName;
    private final boolean secureCookie;

    public AuthSessionService(
        @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName,
        @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie
    ) {
        this.sessionCookieName = sessionCookieName;
        this.secureCookie = secureCookie;
    }

    public void startAuthenticatedSession(HttpServletRequest request) {
        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(Math.toIntExact(MAX_SESSION_LIFETIME.toSeconds()));
        session.setAttribute(EXPIRES_AT_ATTRIBUTE, Instant.now().plus(MAX_SESSION_LIFETIME));
    }

    public boolean isExpired(HttpSession session) {
        Object expiresAt = session.getAttribute(EXPIRES_AT_ATTRIBUTE);
        return expiresAt instanceof Instant expiration && !Instant.now().isBefore(expiration);
    }

    public void logout(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);

        String cookiePath = request.getContextPath().isBlank() ? "/" : request.getContextPath();
        ResponseCookie expiredCookie = ResponseCookie.from(sessionCookieName, "")
            .path(cookiePath)
            .maxAge(Duration.ZERO)
            .httpOnly(true)
            .secure(secureCookie)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
