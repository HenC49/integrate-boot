package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.TokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ResourceServerIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "integrate-boot.resource-server.permit-all-paths=/public/**")
class ResourceServerIntegrationTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    TokenValidationPort validationPort;

    @Test
    void requiresBearerTokenForProtectedPath() throws Exception {
        assertThat(get("/private", null).statusCode()).isEqualTo(401);
        assertThat(get("/private", "Bearer valid-token").statusCode()).isEqualTo(200);
        assertThat(((Application.ValidationPort) validationPort).lastToken).isEqualTo("valid-token");
    }

    @Test
    void permitsConfiguredPublicPath() throws Exception {
        assertThat(get("/public/value", null).statusCode()).isEqualTo(200);
    }

    @Test
    void rejectsInvalidBearerToken() throws Exception {
        assertThat(get("/private", "Bearer invalid-token").statusCode()).isEqualTo(401);
        assertThat(get("/private", "Basic anything").statusCode()).isEqualTo(401);
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
            return new ValidationPort();
        }

        static class ValidationPort implements TokenValidationPort {
            private String lastToken;

            @Override
            public TokenValidationResult validate(String token) {
                lastToken = token;
                return "valid-token".equals(token)
                        ? TokenValidationResult.valid("user-1", Map.of("scope", "read"), Set.of("SCOPE_read"), null)
                        : TokenValidationResult.invalid();
            }
        }

        @RestController
        static class Controller {
            @GetMapping(value = {"/private", "/public/value"}, produces = MediaType.TEXT_PLAIN_VALUE)
            String value() {
                return "ok";
            }
        }
    }
}
