package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.TokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the authorization-server module and the resource-server module are both on the
 * classpath (monolith that both issues and validates tokens), the resource-server filter
 * chain must take deterministic precedence for the application's own endpoints: a bearer
 * token is validated through the app's {@link TokenValidationPort}, not through the
 * authorization-server module's JWT chain.
 */
@SpringBootTest(classes = ModulesCoexistIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ModulesCoexistIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void resourceServerChainValidatesThroughTokenValidationPort() throws Exception {
        // The port accepts exactly this opaque token; the auth-server JWT chain would reject it.
        HttpResponse<String> response = get("/api/me", "Bearer coexist-token");

        assertThat(response.statusCode()).as("the resource-server chain must own the app endpoints, body=%s", response.body())
                .isEqualTo(200);
        assertThat(response.body()).isEqualTo("user-1");
    }

    @Test
    void unauthenticatedRequestsGet401NotARedirect() throws Exception {
        HttpResponse<String> response = get("/api/me", null);

        // The auth-server module's convenience chain enables formLogin (302 to /login);
        // the resource-server chain answers 401. 401 proves which chain is in charge.
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SpringBootApplication
    static class Application {

        @Bean
        TokenValidationPort tokenValidationPort() {
            return token -> "coexist-token".equals(token)
                    ? TokenValidationResult.valid("user-1", java.util.Map.of(), java.util.Set.of(), null)
                    : TokenValidationResult.invalid();
        }

        @RestController
        static class Controller {
            @GetMapping(value = "/api/me", produces = MediaType.TEXT_PLAIN_VALUE)
            String me(org.springframework.security.core.Authentication authentication) {
                return authentication.getName();
            }
        }
    }
}
