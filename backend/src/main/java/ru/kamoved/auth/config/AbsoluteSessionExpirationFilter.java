package ru.kamoved.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.kamoved.auth.application.AuthSessionService;

import java.io.IOException;

final class AbsoluteSessionExpirationFilter extends OncePerRequestFilter {

    private final AuthSessionService authSessionService;

    AbsoluteSessionExpirationFilter(AuthSessionService authSessionService) {
        this.authSessionService = authSessionService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && authSessionService.isExpired(session)) {
            authSessionService.logout(
                request,
                response,
                SecurityContextHolder.getContext().getAuthentication()
            );
        }

        filterChain.doFilter(request, response);
    }
}
