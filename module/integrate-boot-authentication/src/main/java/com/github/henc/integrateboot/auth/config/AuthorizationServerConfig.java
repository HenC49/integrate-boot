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
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.time.Duration;
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
 * </ul>
 */
@AutoConfiguration
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
            RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            PasswordEncoder passwordEncoder, UserDetailsService userDetailsService) throws Exception {

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();
        authorizationServerConfigurer.registeredClientRepository(registeredClientRepository);
        authorizationServerConfigurer.authorizationService(authorizationService);
        authorizationServerConfigurer.tokenGenerator(tokenGenerator);

        // Register the custom password grant on the token endpoint, if enabled.
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
                .apply(authorizationServerConfigurer);
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
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
     * Adds the {@code iss} (issuer) claim to access-token JWTs.
     */
    @Bean
    @ConditionalOnMissingBean(name = "integrateBootJwtCustomizer")
    public OAuth2TokenCustomizer<JwtEncodingContext> integrateBootJwtCustomizer() {
        return context -> {
            // The issuer is resolved from the authorization-server context by Spring's defaults;
            // this hook is the extension point for adding extra claims per-application.
        };
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
     * A demo registered client ({@code client}/{@code secret}) so the authorization server is
     * usable without YAML. Backs off when Spring Boot's auto-configuration provides a
     * {@link RegisteredClientRepository} from {@code spring.security.oauth2.authorization-server.client.*}.
     */
    @Bean
    @ConditionalOnMissingBean(RegisteredClientRepository.class)
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("client")
                .clientSecret(passwordEncoder.encode("secret"))
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
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
        return new InMemoryRegisteredClientRepository(client);
    }

    /**
     * Resource-protection filter chain for the application's own endpoints. Validates JWT bearer
     * tokens issued by the authorization server above, and permits the configured public paths.
     * Business apps can override this bean ({@code @ConditionalOnMissingBean}) to customise
     * authorization rules.
     */
    @Bean
    @Order(2)
    @ConditionalOnMissingBean(name = "defaultSecurityFilterChain")
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
                        org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
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
