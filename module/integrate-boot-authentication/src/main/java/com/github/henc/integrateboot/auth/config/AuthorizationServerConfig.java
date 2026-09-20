package com.github.henc.integrateboot.auth.config;

import com.github.henc.integrateboot.auth.AuthConst;
import com.github.henc.integrateboot.auth.password.OAuth2PasswordAuthenticationConverter;
import com.github.henc.integrateboot.auth.password.OAuth2PasswordAuthenticationProvider;
import com.github.henc.integrateboot.auth.user.UserDetailsPasswordService;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 authorization-server wiring for integrate-boot.
 *
 * <p>Builds on the Spring Boot authorization-server starter (which contributes the registered
 * clients, issuer and JWK auto-configuration). This class adds the parts that make
 * username/password login work end to end:
 * <ul>
 *   <li>A {@link SecurityFilterChain} for the protocol endpoints, with the custom
 *       {@code password} grant converter/provider registered on the token endpoint.</li>
 *   <li>A composite {@link OAuth2TokenGenerator} (JWT access + opaque access + refresh) shared by
 *       the standard and password grants.</li>
 *   <li>A {@link PasswordEncoder} and a placeholder {@link UserDetailsService} so the password
 *       grant has something to authenticate against until the application plugs in its own user
 *       store via {@link UserDetailsPasswordService}.</li>
 *   <li>A demo public client ({@code pkce-client}, no secret) restricted to the
 *       authorization-code grant with mandatory PKCE, so secret-less browser/SPA clients work
 *       out of the box (RFC 7636).</li>
 * </ul>
 */
@AutoConfiguration(afterName =
        "com.github.henc.integrateboot.resource.config.ResourceServerAutoConfiguration")
@ConditionalOnClass({OAuth2AuthorizationServerConfigurer.class, RegisteredClientRepository.class})
@EnableConfigurationProperties(AuthenticationProperties.class)
public class AuthorizationServerConfig {

    /**
     * Protocol-endpoint filter chain: applies the authorization-server defaults (token,
     * authorization, jwk-set, ... endpoints) and registers the custom password grant. Runs at a
     * higher priority than the application's resource-protection filter chain.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http, AuthenticationProperties properties,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            PasswordEncoder passwordEncoder, UserDetailsService userDetailsService) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        // Register the custom password grant on the token endpoint, if enabled. The
        // registered-client repository / authorization service / token generator are resolved
        // from the bean container by Spring's OAuth2ConfigurerUtils — the configurer's setters
        // may only be called after it is applied to the builder.
        if (properties.isPasswordGrantEnabled()) {
            OAuth2PasswordAuthenticationProvider passwordProvider =
                    new OAuth2PasswordAuthenticationProvider(
                            userDetailsService, passwordEncoder, authorizationService);
            passwordProvider.setTokenGenerator(tokenGenerator);
            authorizationServerConfigurer.tokenEndpoint(tokenEndpoint -> tokenEndpoint
                    .accessTokenRequestConverter(new OAuth2PasswordAuthenticationConverter())
                    .authenticationProvider(passwordProvider));
        }

        // Limit this chain to the authorization-server endpoints.
        RequestMatcher endpointsMatcher = authorizationServerConfigurer.getEndpointsMatcher();

        http
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .with(authorizationServerConfigurer, Customizer.withDefaults())
                // Unauthenticated browser requests to /oauth2/authorize (the human in the
                // authorization-code + PKCE flow) are redirected to the login page; API clients
                // (Accept: anything but text/html) keep the resource-server default of 401 with
                // WWW-Authenticate. Without this mapping the browser flow cannot start.
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        browserRequestMatcher()))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Matches requests whose {@code Accept} header signals a browser ({@code text/html}). A bare
     * accept-anything header is ignored, so programmatic clients still receive the 401 bearer
     * challenge instead of a login-page redirect.
     */
    private static RequestMatcher browserRequestMatcher() {
        MediaTypeRequestMatcher browserMatcher = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);
        browserMatcher.setIgnoredMediaTypes(Set.of(MediaType.ALL));
        return browserMatcher;
    }

    /**
     * JWT encoder backed by the JWK source, used by the {@link JwtGenerator}.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * Composite token generator: a {@link JwtGenerator} for access tokens (carrying claims),
     * a fallback {@link OAuth2AccessTokenGenerator} for opaque tokens, and an
     * {@link OAuth2RefreshTokenGenerator} for refresh tokens. Shared by every grant type.
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(
            JwtEncoder jwtEncoder, OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        OAuth2RefreshTokenGenerator refreshTokenGenerator = new OAuth2RefreshTokenGenerator();
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator, accessTokenGenerator, refreshTokenGenerator);
    }

    /**
     * Extension point for customizing access-token JWTs. The {@code iss} claim is added by the
     * {@link JwtGenerator} itself (resolved from the authorization-server context); applications
     * that want additional claims define their own bean to override this no-op default.
     */
    @Bean
    @ConditionalOnMissingBean(name = "integrateBootJwtCustomizer")
    public OAuth2TokenCustomizer<JwtEncodingContext> integrateBootJwtCustomizer() {
        return context -> {
        };
    }

    /**
     * In-memory authorization store persisting issued access/refresh tokens. Spring Boot's
     * authorization-server auto-configuration does <em>not</em> provide this bean, so without a
     * default here the token endpoint cannot start. Applications that run multiple instances
     * should override it with a JDBC/Redis-backed implementation (and likewise the
     * {@link RegisteredClientRepository}).
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService oAuth2AuthorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    /**
     * Password encoder for verifying submitted passwords. Defaults to the delegating encoder
     * (BCrypt-friendly); override with a bean of type {@link PasswordEncoder} to change it.
     */
    @Bean
    @ConditionalOnMissingBean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Placeholder user store so the password grant has a deterministic user to authenticate
     * against out of the box ({@code user}/{@code password}). Applications should define their
     * own {@link UserDetailsPasswordService} bean to back this with a real user store — this bean
     * then backs off via {@code @ConditionalOnMissingBean}.
     */
    @Bean
    @ConditionalOnMissingBean({UserDetailsService.class, UserDetailsPasswordService.class})
    public UserDetailsService defaultUserDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails defaultUser = User.withUsername("user")
                .password(passwordEncoder.encode("password"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(defaultUser);
    }

    /**
     * Demo registered clients so the authorization server is usable without YAML:
     * <ul>
     *   <li>{@code client}/{@code secret} — a confidential client for the password /
     *       client-credentials / refresh grants.</li>
     *   <li>{@code pkce-client} — a <em>public</em> client (no secret) restricted to the
     *       authorization-code grant with mandatory PKCE, demonstrating secret-free operation
     *       for browser / SPA / mobile clients.</li>
     * </ul>
     * Backs off when Spring Boot's auto-configuration provides a
     * {@link RegisteredClientRepository} from {@code spring.security.oauth2.authorization-server.client.*}.
     */
    @Bean
    @ConditionalOnMissingBean(RegisteredClientRepository.class)
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        return new InMemoryRegisteredClientRepository(
                demoConfidentialClient(passwordEncoder), demoPkceClient());
    }

    /**
     * A confidential demo client ({@code client}/{@code secret}) exercising the grants that
     * authenticate with a client secret.
     */
    private static RegisteredClient demoConfidentialClient(PasswordEncoder passwordEncoder) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("client")
                .clientSecret(passwordEncoder.encode("secret"))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthConst.PASSWORD_GRANT_TYPE)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/client")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .build())
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .scope("read")
                .scope("write")
                .build();
    }

    /**
     * A public demo client ({@code pkce-client}, no secret) for the authorization-code grant
     * with PKCE (RFC 7636). It authenticates with {@link ClientAuthenticationMethod#NONE} —
     * only its {@code client_id} is presented, never a secret — and
     * {@link ClientSettings.Builder#requireProofKey(boolean) requireProofKey(true)} makes the
     * authorization endpoint reject any request without a {@code code_challenge}, so a stolen
     * authorization code cannot be redeemed without the verifier held by the original client.
     *
     * <p>The {@code refresh_token} grant is deliberately <em>not</em> registered: Spring
     * Authorization Server refuses to issue refresh tokens to public clients on the
     * authorization-code grant, because a bearer refresh token cannot be bound to a client
     * that authenticates with {@code none} — whoever stole it could redeem it (RFC 6749
     * §10.5). Confidential clients keep the grant; public clients that need renewal require
     * sender-constrained refresh tokens (mTLS / DPoP or a custom {@code OAuth2TokenGenerator}).
     */
    private static RegisteredClient demoPkceClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("pkce-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://127.0.0.1:8080/login/oauth2/code/pkce-client")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .scope("read")
                .scope("write")
                .build();
    }

    /**
     * Resource-protection filter chain for the application's own endpoints. Validates JWT bearer
     * tokens issued by the authorization server above, and permits the configured public paths.
     * Business apps can override this bean ({@code @ConditionalOnMissingBean}) to customise
     * authorization rules.
     *
     * <p>Backs off when the {@code integrate-boot-resource-server} module provides its own chain
     * ({@code resourceServerSecurityFilterChain}): Spring Security 7 rejects two
     * matches-any-request chains in one application ({@code UnreachableFilterChainException}),
     * so exactly one default chain may exist.
     *
     * <p>Sessions are {@link SessionCreationPolicy#IF_REQUIRED IF_REQUIRED} (not stateless)
     * because the authorization-code + PKCE flow is a browser flow: the resource owner submits
     * the login form on {@code POST /login} (this chain) and that authentication must live in
     * the HTTP session until the authorization endpoint ({@code /oauth2/authorize}, the protocol
     * chain) issues the code. With STATELESS the login would be forgotten between the two
     * requests and the browser would bounce between {@code /login} and {@code /oauth2/authorize}
     * forever. Bearer-token API traffic stays effectively stateless: under Spring Security's
     * explicit-save semantics only interactive login (form / basic) persists a context, JWT
     * bearer authentication does not.
     */
    @Bean
    @Order(2)
    @ConditionalOnMissingBean(name = {"defaultSecurityFilterChain", "resourceServerSecurityFilterChain"})
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http,
                                                          AuthenticationProperties properties) throws Exception {
        String[] permitAll = permitAllPaths(properties);
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(request -> true))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(permitAll).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.IF_REQUIRED))
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    private static String[] permitAllPaths(AuthenticationProperties properties) {
        java.util.List<String> paths = new java.util.ArrayList<>(AuthConst.DEFAULT_PERMIT_ALL_PATHS);
        if (properties.getPermitAllPaths() != null) {
            paths.addAll(properties.getPermitAllPaths());
        }
        return paths.toArray(new String[0]);
    }
}
