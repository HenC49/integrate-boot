package com.github.henc.integrateboot.auth.pkce;

import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the authorization-code grant with PKCE (RFC 7636) against the module's
 * out-of-the-box authorization server, using the demo public client {@code pkce-client}, the
 * demo confidential client {@code client}/{@code secret} and the demo user
 * {@code user}/{@code password}.
 *
 * <p>The flow is driven exactly like a browser / SPA would drive it: the resource owner logs in
 * through the form-login page, the authorization request carries an S256 {@code code_challenge},
 * and the code is redeemed <em>without any client secret</em> — only the {@code client_id} and
 * the one-time {@code code_verifier} are presented. That is the point of PKCE: a code
 * intercepted from the front channel is useless without the verifier, so a public client never
 * needs to ship a secret.
 */
@SpringBootTest(classes = PkceAuthorizationCodeGrantIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PkceAuthorizationCodeGrantIntegrationTest {

    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();

    private static final String PKCE_CLIENT = "pkce-client";
    private static final String PKCE_REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/pkce-client";
    private static final String CONFIDENTIAL_CLIENT = "client";
    private static final String CONFIDENTIAL_REDIRECT_URI = "http://127.0.0.1:8080/login/oauth2/code/client";

    /** Shares cookies (JSESSIONID) across requests, like a browser would. */
    private final CookieManager cookieManager = new CookieManager();

    /** Redirects are followed manually so each hop (login, authorize, callback) can be asserted. */
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @LocalServerPort
    int port;

    @Test
    void authorizationCodeWithPkceIssuesTokensWithoutClientSecret() throws Exception {
        String verifier = codeVerifier();
        String state = UUID.randomUUID().toString();

        // 1. Unauthenticated request to the authorization endpoint -> redirected to the login page.
        HttpResponse<String> loginRedirect = get(authorizeUrl(PKCE_CLIENT, PKCE_REDIRECT_URI, verifier, state));
        assertThat(loginRedirect.statusCode()).isEqualTo(302);
        assertThat(location(loginRedirect)).contains("/login");

        // 2. The resource owner submits the default form-login page.
        HttpResponse<String> afterLogin = postForm("/login", "username=user&password=password");
        assertThat(afterLogin.statusCode()).as("form login must succeed, body=%s", afterLogin.body())
                .isEqualTo(302);
        // Back to the saved authorization request...
        assertThat(location(afterLogin)).contains("/oauth2/authorize");

        // 3. ...which now issues the code: 302 to the registered redirect_uri.
        HttpResponse<String> callback = get(location(afterLogin));
        assertThat(callback.statusCode()).as("authorization must issue a code, body=%s", callback.body())
                .isEqualTo(302);
        String redirect = location(callback);
        assertThat(redirect).startsWith(PKCE_REDIRECT_URI);
        String code = queryParam(redirect, "code");
        assertThat(code).isNotBlank();
        // The state round-trips untouched (CSRF protection for the front channel).
        assertThat(queryParam(redirect, "state")).isEqualTo(state);

        // 4. Redeem the code WITHOUT any client credentials — client_id + code_verifier only.
        HttpResponse<String> tokenResponse = postForm("/oauth2/token",
                redeemForm(PKCE_CLIENT, PKCE_REDIRECT_URI, code, verifier));

        assertThat(tokenResponse.statusCode())
                .as("public client must redeem the code without a secret, body=%s", tokenResponse.body())
                .isEqualTo(200);
        JsonNode tokens = toJson(tokenResponse);
        assertThat(tokens.get("access_token").asText()).isNotBlank();
        assertThat(tokens.get("token_type").asText()).isEqualTo("Bearer");
        assertThat(tokens.get("scope").asText()).isEqualTo("read");
        // Spring Authorization Server deliberately issues no refresh token to a public client:
        // a bearer refresh token cannot be bound to a client that authenticates with "none".
        assertThat(tokens.get("refresh_token")).as("refresh tokens must not be issued to public clients")
                .isNull();
    }

    @Test
    void accessTokenFromPkceFlowUnlocksProtectedEndpoint() throws Exception {
        String verifier = codeVerifier();
        JsonNode tokens = toJson(postForm("/oauth2/token",
                redeemForm(PKCE_CLIENT, PKCE_REDIRECT_URI, authorizeAndLogin(PKCE_CLIENT, PKCE_REDIRECT_URI, verifier), verifier)));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Bearer " + tokens.get("access_token").asText())
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("user");
    }

    @Test
    void confidentialClientUsingPkceGetsRefreshableTokens() throws Exception {
        // PKCE also hardens confidential clients (defense in depth for a leaked code), and the
        // confidential client authenticates at the token endpoint with its secret as usual.
        String verifier = codeVerifier();
        String code = authorizeAndLogin(CONFIDENTIAL_CLIENT, CONFIDENTIAL_REDIRECT_URI, verifier);

        HttpResponse<String> tokenResponse = postFormWithBasic(CONFIDENTIAL_CLIENT, "secret",
                redeemForm(CONFIDENTIAL_CLIENT, CONFIDENTIAL_REDIRECT_URI, code, verifier));

        assertThat(tokenResponse.statusCode())
                .as("confidential client must redeem the code, body=%s", tokenResponse.body())
                .isEqualTo(200);
        JsonNode tokens = toJson(tokenResponse);
        assertThat(tokens.get("access_token").asText()).isNotBlank();
        assertThat(tokens.get("refresh_token").asText()).isNotBlank();

        HttpResponse<String> refreshed = postFormWithBasic(CONFIDENTIAL_CLIENT, "secret",
                "grant_type=refresh_token&refresh_token=" + urlEncode(tokens.get("refresh_token").asText()));
        assertThat(refreshed.statusCode()).as("body=%s", refreshed.body()).isEqualTo(200);
        assertThat(toJson(refreshed).get("access_token").asText()).isNotBlank();
    }

    @Test
    void tokenExchangeRejectsWrongCodeVerifier() throws Exception {
        String code = authorizeAndLogin(PKCE_CLIENT, PKCE_REDIRECT_URI, codeVerifier());

        // A different (structurally valid) verifier: what an attacker who stole only the code could send.
        HttpResponse<String> response = postForm("/oauth2/token",
                redeemForm(PKCE_CLIENT, PKCE_REDIRECT_URI, code, codeVerifier()));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(toJson(response).get("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void tokenExchangeRejectsMissingCodeVerifier() throws Exception {
        String verifier = codeVerifier();
        String code = authorizeAndLogin(PKCE_CLIENT, PKCE_REDIRECT_URI, verifier);

        HttpResponse<String> response = postForm("/oauth2/token",
                "grant_type=authorization_code"
                        + "&client_id=" + PKCE_CLIENT
                        + "&code=" + urlEncode(code)
                        + "&redirect_uri=" + urlEncode(PKCE_REDIRECT_URI));

        // For a client authenticating with "none", the code_verifier is part of client
        // authentication (RFC 6749 §3.2.1) — its absence fails there, before the grant check.
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(toJson(response).get("error").asText()).isEqualTo("invalid_client");
    }

    @Test
    void authorizationRequestWithoutCodeChallengeIsRejected() throws Exception {
        // pkce-client has requireProofKey(true): no code_challenge -> no code, only an error.
        HttpResponse<String> response = get(authorizeUrl(PKCE_CLIENT, PKCE_REDIRECT_URI, null, null));

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(location(response))
                .startsWith(PKCE_REDIRECT_URI)
                .contains("error=invalid_request")
                .doesNotContain("code=");
    }

    // ------------------------------------------------------------------ flow helpers

    /**
     * Drives the browser part of the flow (authorize → login → authorize → code) and returns the
     * authorization code.
     */
    private String authorizeAndLogin(String clientId, String redirectUri, String verifier)
            throws Exception {
        HttpResponse<String> loginRedirect = get(authorizeUrl(clientId, redirectUri, verifier, null));
        assertThat(loginRedirect.statusCode()).isEqualTo(302);

        HttpResponse<String> afterLogin = postForm("/login", "username=user&password=password");
        assertThat(afterLogin.statusCode()).as("form login must succeed, body=%s", afterLogin.body())
                .isEqualTo(302);

        HttpResponse<String> callback = get(location(afterLogin));
        assertThat(callback.statusCode()).as("authorization must issue a code, body=%s", callback.body())
                .isEqualTo(302);
        String code = queryParam(location(callback), "code");
        assertThat(code).isNotBlank();
        return code;
    }

    /** Builds an authorization URL; the S256 challenge is included when a verifier is given. */
    private String authorizeUrl(String clientId, String redirectUri, String verifier, String state) {
        return "/oauth2/authorize?response_type=code&client_id=" + clientId
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&scope=read"
                + (verifier != null
                        ? "&code_challenge=" + s256(verifier) + "&code_challenge_method=S256"
                        : "")
                + (state != null ? "&state=" + urlEncode(state) : "");
    }

    private String redeemForm(String clientId, String redirectUri, String code, String verifier) {
        return "grant_type=authorization_code"
                + "&client_id=" + clientId
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&code_verifier=" + urlEncode(verifier);
    }

    // ------------------------------------------------------------------ PKCE math (RFC 7636)

    /** 32 random bytes, base64url-encoded without padding: a 43-char valid code_verifier. */
    private static String codeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** code_challenge = BASE64URL(SHA-256(ASCII(code_verifier))) — the S256 method. */
    private static String s256(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    // ------------------------------------------------------------------ http helpers

    /** Browser-style GET: {@code Accept: text/html} makes the auth chain redirect to /login. */
    private HttpResponse<String> get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(absolute(url)))
                .header("Accept", "text/html")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(absolute(path)))
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Token request with HTTP Basic client authentication, as a confidential client sends it. */
    private HttpResponse<String> postFormWithBasic(String username, String password, String form)
            throws IOException, InterruptedException {
        String basic = Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(absolute("/oauth2/token")))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Accepts either a server-relative path or a Location header. Location headers produced by
     * this server already point at the test port; the registered redirect URI (port 8080) is
     * never fetched — only its query string is parsed for the code.
     */
    private String absolute(String url) {
        if (url.startsWith("http")) {
            return url;
        }
        return "http://localhost:" + port + url;
    }

    private static String location(HttpResponse<String> response) {
        return response.headers().firstValue("Location").orElse("");
    }

    private static String queryParam(String url, String name) {
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return "";
        }
        for (String param : query.split("&")) {
            int eq = param.indexOf('=');
            String key = eq < 0 ? param : param.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : urlDecode(param.substring(eq + 1));
            }
        }
        return "";
    }

    private static String urlDecode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
