package com.github.henc.integrateboot.auth.password;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the custom {@code password} grant against the module's out-of-the-box
 * authorization server (demo client {@code client}/{@code secret}, demo user
 * {@code user}/{@code password}): issuing tokens via password, refreshing them via the
 * standard {@code refresh_token} grant, and calling a protected endpoint with the JWT.
 */
@SpringBootTest(classes = PasswordGrantIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordGrantIntegrationTest {

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Test
    void passwordGrantIssuesAccessTokenAndRefreshToken() throws Exception {
        HttpResponse<String> response = tokenRequest("grant_type=password&username=user&password=password&scope=read");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = toJson(response);
        assertThat(body.get("access_token").asText()).isNotBlank();
        assertThat(body.get("refresh_token").asText()).isNotBlank();
        assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
    }

    @Test
    void passwordGrantRejectsWrongPassword() throws Exception {
        HttpResponse<String> response = tokenRequest("grant_type=password&username=user&password=wrong");

        // RFC 6749 §5.2: invalid_grant is reported as 400 with an error body.
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(toJson(response).get("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void refreshTokenIssuedByPasswordGrantCanBeRefreshed() throws Exception {
        JsonNode tokens = toJson(tokenRequest("grant_type=password&username=user&password=password&scope=read"));
        String refreshToken = tokens.get("refresh_token").asText();

        HttpResponse<String> response = tokenRequest("grant_type=refresh_token&refresh_token=" + refreshToken);

        assertThat(response.statusCode()).as("refresh_token grant must succeed for tokens issued via the password grant, body=%s", response.body())
                .isEqualTo(200);
        JsonNode body = toJson(response);
        assertThat(body.get("access_token").asText()).isNotBlank();
    }

    @Test
    void accessTokenFromPasswordGrantUnlocksProtectedEndpoint() throws Exception {
        JsonNode tokens = toJson(tokenRequest("grant_type=password&username=user&password=password&scope=read"));
        String accessToken = tokens.get("access_token").asText();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("user");
    }

    private HttpResponse<String> tokenRequest(String form) throws IOException, InterruptedException {
        String basic = Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode toJson(HttpResponse<String> response) throws IOException {
        return MAPPER.readTree(response.body());
    }

    @SpringBootApplication
    static class Application {

        @RestController
        static class Controller {
            @GetMapping(value = "/api/me", produces = MediaType.TEXT_PLAIN_VALUE)
            String me(org.springframework.security.core.Authentication authentication) {
                return authentication.getName();
            }
        }
    }
}
