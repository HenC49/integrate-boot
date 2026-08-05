package com.github.henc.integrateboot.auth.password;

import com.github.henc.integrateboot.auth.AuthConst;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Carries the parameters of a {@code grant_type=password} token request through the
 * authentication pipeline — from the {@link OAuth2PasswordAuthenticationConverter converter}
 * (which extracts them from the HTTP request) to the
 * {@link OAuth2PasswordAuthenticationProvider provider} (which authenticates the user and mints
 * the access token).
 *
 * <p>Holds the resource owner's {@code username}/{@code password}, the requested {@code scopes}
 * and the authenticated client principal (the {@link OAuth2PasswordAuthenticationProvider} uses
 * the client principal to build the resulting {@code OAuth2Authorization}).
 */
public class OAuth2PasswordAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    private final String username;
    private final String password;
    private final Set<String> scopes;

    /**
     * @param username        the resource owner username
     * @param password        the resource owner password (raw)
     * @param clientPrincipal the authenticated client ({@link
     *                        org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken})
     * @param scopes          the requested scopes, or empty if none
     * @param additionalParameters non-standard request parameters to carry through
     */
    public OAuth2PasswordAuthenticationToken(String username, String password,
                                             Authentication clientPrincipal, Set<String> scopes,
                                             Map<String, Object> additionalParameters) {
        super(AuthConst.PASSWORD_GRANT_TYPE, clientPrincipal,
                additionalParameters == null ? Collections.emptyMap() : additionalParameters);
        this.username = username;
        this.password = password;
        this.scopes = scopes == null ? Collections.emptySet() : new HashSet<>(scopes);
    }

    @Override
    public Object getCredentials() {
        return this.password;
    }

    public String getUsername() {
        return this.username;
    }

    public String getPassword() {
        return this.password;
    }

    public Set<String> getScopes() {
        return Collections.unmodifiableSet(this.scopes);
    }
}
