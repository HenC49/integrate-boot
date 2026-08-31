package com.github.henc.integrateboot.auth.password;

import com.github.henc.integrateboot.auth.AuthConst;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Authenticates a {@code grant_type=password} request: resolves the resource owner through the
 * configured {@link UserDetailsService}, verifies the password with a {@link PasswordEncoder},
 * then mints an access token (and refresh token if the client supports it) via the shared
 * {@link OAuth2TokenGenerator} and persists the authorization through
 * {@link OAuth2AuthorizationService}.
 */
public class OAuth2PasswordAuthenticationProvider implements AuthenticationProvider {

    /** Attribute key under which the original password-grant token is stashed on the authorization. */
    public static final String PASSWORD_GRANT_ATTRIBUTE_KEY =
            "org.springframework.security.oauth2.server.authorization.authentication.OAuth2PasswordAuthenticationToken.principal";

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationService authorizationService;
    private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    /**
     * @param userDetailsService   resolves the resource owner by username
     * @param passwordEncoder      verifies the submitted password against the stored hash
     * @param authorizationService persists the resulting OAuth2Authorization
     */
    public OAuth2PasswordAuthenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder, OAuth2AuthorizationService authorizationService) {
        Assert.notNull(userDetailsService, "userDetailsService cannot be null");
        Assert.notNull(passwordEncoder, "passwordEncoder cannot be null");
        Assert.notNull(authorizationService, "authorizationService cannot be null");
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authorizationService = authorizationService;
    }

    /**
     * Set the token generator used to mint access / refresh tokens. Required before the provider
     * can issue tokens.
     */
    public void setTokenGenerator(OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2PasswordAuthenticationToken passwordAuthentication =
                (OAuth2PasswordAuthenticationToken) authentication;

        // Resolve the client that is making the request (authenticated by OAuth2ClientAuthenticationFilter).
        Authentication clientPrincipal = (Authentication) passwordAuthentication.getPrincipal();
        OAuth2ClientAuthenticationToken clientAuthentication = resolveClientAuthentication(clientPrincipal);
        RegisteredClient registeredClient = clientAuthentication != null
                ? clientAuthentication.getRegisteredClient() : null;
        if (registeredClient == null) {
            throwInvalidGrant("client authentication failed");
        }

        // Authenticate the resource owner.
        UserDetails userDetails = userDetailsService.loadUserByUsername(passwordAuthentication.getUsername());
        if (userDetails == null) {
            throwInvalidGrant("invalid username or password");
        }
        if (!passwordEncoder.matches(passwordAuthentication.getPassword(), userDetails.getPassword())) {
            throwInvalidGrant("invalid username or password");
        }

        // Re-authenticate the user so the SecurityContext reflects the resource owner.
        UsernamePasswordAuthenticationToken usernamePasswordAuthentication =
                new UsernamePasswordAuthenticationToken(userDetails, userDetails.getPassword(),
                        userDetails.getAuthorities());

        // Compute authorized scopes: the intersection of requested and client-allowed scopes.
        Set<String> authorizedScopes = intersectScopes(
                passwordAuthentication.getScopes(), registeredClient.getScopes());

        // Build the token context used by the shared token generator.
        DefaultOAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(usernamePasswordAuthentication)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(AuthConst.PASSWORD_GRANT_TYPE)
                .authorizationGrant(passwordAuthentication)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();

        OAuth2Token generatedAccessToken = tokenGenerator.generate(tokenContext);
        if (generatedAccessToken == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.SERVER_ERROR,
                    "The token generator failed to generate the access token.",
                    "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2"));
        }

        OAuth2AccessToken accessToken = toAccessToken(generatedAccessToken, authorizedScopes);

        // Mint a refresh token when the client is authorized for it.
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            DefaultOAuth2TokenContext refreshContext = DefaultOAuth2TokenContext.builder()
                    .registeredClient(registeredClient)
                    .principal(usernamePasswordAuthentication)
                    .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                    .authorizedScopes(authorizedScopes)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrant(passwordAuthentication)
                    .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                    .build();
            OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshContext);
            if (generatedRefreshToken != null) {
                refreshToken = toRefreshToken(generatedRefreshToken);
            }
        }

        // Persist the authorization. The attribute under Principal.class.getName() is what
        // Spring Authorization Server's built-in refresh_token provider reads back when the
        // client later refreshes — without it the refresh grant fails with
        // "principal cannot be null".
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userDetails.getUsername())
                .authorizationGrantType(AuthConst.PASSWORD_GRANT_TYPE)
                .authorizedScopes(authorizedScopes)
                .attribute(PASSWORD_GRANT_ATTRIBUTE_KEY, passwordAuthentication)
                .attribute(Principal.class.getName(), usernamePasswordAuthentication);
        if (generatedAccessToken instanceof ClaimAccessor claimAccessor) {
            authorizationBuilder.token(accessToken, metadata -> metadata.put(
                    OAuth2Authorization.Token.CLAIMS_METADATA_NAME, claimAccessor.getClaims()));
        } else {
            authorizationBuilder.accessToken(accessToken);
        }
        if (refreshToken != null) {
            authorizationBuilder.refreshToken(refreshToken);
        }
        OAuth2Authorization authorization = authorizationBuilder.build();
        authorizationService.save(authorization);

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal,
                accessToken, refreshToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2PasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2ClientAuthenticationToken resolveClientAuthentication(Authentication principal) {
        if (principal instanceof OAuth2ClientAuthenticationToken clientAuth && clientAuth.isAuthenticated()) {
            return clientAuth;
        }
        return null;
    }

    private static Set<String> intersectScopes(Set<String> requested, Set<String> allowed) {
        if (CollectionUtils.isEmpty(requested)) {
            return Collections.emptySet();
        }
        Set<String> intersection = new LinkedHashSet<>(requested);
        intersection.retainAll(allowed);
        return intersection;
    }

    private static OAuth2AccessToken toAccessToken(OAuth2Token token, Set<String> scopes) {
        return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt(), scopes);
    }

    @SuppressWarnings("deprecation")
    private static OAuth2RefreshToken toRefreshToken(OAuth2Token token) {
        return new OAuth2RefreshToken(token.getTokenValue(), token.getIssuedAt(), token.getExpiresAt());
    }

    private static void throwInvalidGrant(String message) {
        throw new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT, message,
                "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2"));
    }
}
