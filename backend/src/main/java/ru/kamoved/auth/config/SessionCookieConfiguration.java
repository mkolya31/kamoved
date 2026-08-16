package ru.kamoved.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;

@Configuration
public class SessionCookieConfiguration {

    @Bean
    CookieSerializer sessionCookieSerializer(
        @Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName,
        @Value("${server.servlet.session.cookie.max-age:7d}") Duration cookieMaxAge,
        @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
        @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
        @Value("${server.servlet.session.cookie.same-site:Lax}") String sameSite
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(cookieName);
        serializer.setCookiePath("/");
        serializer.setCookieMaxAge(Math.toIntExact(cookieMaxAge.toSeconds()));
        serializer.setUseHttpOnlyCookie(httpOnly);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);

        // Old Tomcat JSESSIONID values can look like Base64 but decode to arbitrary bytes,
        // including NUL. A plain UUID is cookie-safe and makes legacy values harmless misses.
        serializer.setUseBase64Encoding(false);
        return serializer;
    }
}
