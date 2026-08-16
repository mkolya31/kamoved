package ru.kamoved.auth.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import ru.kamoved.KamovedApplication;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSessionRestartIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void restoresAuthenticationFromSharedJdbcStoreAfterBackendRestart() throws Exception {
        String databaseUrl = "jdbc:h2:mem:session_restart_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        try (ConfigurableApplicationContext firstBackend = startBackend(databaseUrl)) {
            URI firstBaseUrl = baseUrl(firstBackend);
            Map<String, String> csrf = csrf(client, firstBaseUrl);

            HttpResponse<String> login = client.send(
                HttpRequest.newBuilder(firstBaseUrl.resolve("/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .header(csrf.get("headerName"), csrf.get("token"))
                    .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "username": "admin",
                          "password": "test-password"
                        }
                        """))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );

            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(me(client, firstBaseUrl).statusCode()).isEqualTo(200);
        }

        try (ConfigurableApplicationContext restartedBackend = startBackend(databaseUrl)) {
            HttpResponse<String> restored = me(client, baseUrl(restartedBackend));

            assertThat(restored.statusCode()).isEqualTo(200);
            assertThat(restored.body()).contains("\"username\":\"admin\"");
        }
    }

    private ConfigurableApplicationContext startBackend(String databaseUrl) {
        return SpringApplication.run(
            KamovedApplication.class,
            "--spring.datasource.url=" + databaseUrl,
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.session.jdbc.initialize-schema=never",
            "--server.port=0",
            "--server.servlet.session.cookie.secure=false",
            "--spring.main.banner-mode=off"
        );
    }

    private URI baseUrl(ConfigurableApplicationContext context) {
        int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }

    private Map<String, String> csrf(HttpClient client, URI baseUrl) throws Exception {
        HttpResponse<String> response = client.send(
            HttpRequest.newBuilder(baseUrl.resolve("/api/auth/csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private HttpResponse<String> me(HttpClient client, URI baseUrl) throws Exception {
        return client.send(
            HttpRequest.newBuilder(baseUrl.resolve("/api/auth/me")).GET().build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }
}
