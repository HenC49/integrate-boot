package com.github.henc.integrateboot.auth;

import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.List;

/**
 * Shared constants for the integrate-boot authentication / OAuth2 authorization server layer.
 */
public final class AuthConst {

    private AuthConst() {
    }

    /**
     * The OAuth2 "password" grant type. It was removed from OAuth 2.1 and from Spring
     * Authorization Server's built-in grants, so this module re-adds it as a custom grant.
     */
    public static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");

    /** Password-grant request parameter: the resource owner's username. */
    public static final String USERNAME = "username";

    /** Password-grant request parameter: the resource owner's password. */
    public static final String PASSWORD = "password";

    /** Password-grant request parameter value. */
    public static final String GRANT_TYPE_PASSWORD = "password";

    /** Password-grant request parameter name. */
    public static final String GRANT_TYPE = "grant_type";

    /** OAuth2 scope request parameter name. */
    public static final String SCOPE = "scope";

    /** OAuth2 client_id request parameter name. */
    public static final String CLIENT_ID = "client_id";

    /** OAuth2 client_secret request parameter name. */
    public static final String CLIENT_SECRET = "client_secret";

    /**
     * Default request paths that are always permitted (no authentication): the OAuth2 protocol
     * endpoints and the actuator health endpoints.
     */
    public static final List<String> DEFAULT_PERMIT_ALL_PATHS = List.of(
            "/oauth2/**",
            "/.well-known/**",
            "/login",
            "/actuator/**"
    );
}
