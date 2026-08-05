package com.github.henc.integrateboot.auth.password;

import com.github.henc.integrateboot.auth.AuthConst;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2ClientCredentialsAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts an OAuth2 token request with {@code grant_type=password} into an
 * {@link OAuth2PasswordAuthenticationToken}. Registered on the token endpoint alongside the
 * built-in converters, so it only activates for the password grant and is transparent to the
 * other grants (authorization_code, client_credentials, refresh_token, ...).
 */
public class OAuth2PasswordAuthenticationConverter implements AuthenticationConverter {

    private final OAuth2ClientCredentialsAuthenticationConverter clientCredentialsDelegate =
            new OAuth2ClientCredentialsAuthenticationConverter();

    /** Parameters that the password grant reads explicitly; everything else is "additional". */
    private static final Set<String> RESERVED_PARAMS = Set.of(
            AuthConst.GRANT_TYPE,
            AuthConst.USERNAME,
            AuthConst.PASSWORD,
            AuthConst.SCOPE,
            AuthConst.CLIENT_ID,
            AuthConst.CLIENT_SECRET);

    @Override
    public Authentication convert(HttpServletRequest request) {
        // Only handle the password grant; the built-in delegates handle the rest.
        String grantType = request.getParameter(AuthConst.GRANT_TYPE);
        if (!AuthConst.GRANT_TYPE_PASSWORD.equals(grantType)) {
            return null;
        }

        // Reuse Spring's client-credentials converter to resolve the authenticated client from
        // the request (client_id / client_secret / Basic auth). Its token's principal is the
        // authenticated client — exactly what the provider needs.
        Authentication clientAuthentication = clientCredentialsDelegate.convert(request);
        Authentication clientPrincipal = clientAuthentication != null
                ? clientAuthentication
                : SecurityContextHolder.getContext().getAuthentication();

        MultiValueMap<String, String> parameters = toMultiValueMap(request);

        // Validate required username/password parameters.
        String username = parameters.getFirst(AuthConst.USERNAME);
        String password = parameters.getFirst(AuthConst.PASSWORD);
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)
                || username.trim().isEmpty()) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_REQUEST,
                    "username and password are required",
                    "https://datatracker.ietf.org/doc/html/rfc6749#section-4.3.2"));
        }

        // Collect any non-reserved request params to carry through as additional parameters.
        Map<String, Object> additionalParameters = new HashMap<>();
        parameters.forEach((key, values) -> {
            if (!RESERVED_PARAMS.contains(key) && !values.isEmpty()) {
                additionalParameters.put(key, values.get(0));
            }
        });

        Set<String> scopes = parseScopes(parameters);

        return new OAuth2PasswordAuthenticationToken(
                username.trim(), password, clientPrincipal, scopes, additionalParameters);
    }

    private static Set<String> parseScopes(MultiValueMap<String, String> parameters) {
        String scope = parameters.getFirst(AuthConst.SCOPE);
        if (!StringUtils.hasText(scope)) {
            return Collections.emptySet();
        }
        Set<String> scopes = new HashSet<>();
        for (String token : StringUtils.delimitedListToStringArray(scope, " ")) {
            if (StringUtils.hasText(token)) {
                scopes.add(token);
            }
        }
        return scopes;
    }

    private static MultiValueMap<String, String> toMultiValueMap(HttpServletRequest request) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        request.getParameterMap().forEach((key, values) -> {
            for (String value : values) {
                map.add(key, value);
            }
        });
        return map;
    }
}
