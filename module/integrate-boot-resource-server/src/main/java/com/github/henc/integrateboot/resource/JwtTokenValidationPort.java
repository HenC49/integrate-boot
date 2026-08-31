package com.github.henc.integrateboot.resource;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default {@link TokenValidationPort} backed by Spring Security's {@link JwtDecoder}
 * (remote JWKS verification). Maps JWT claims onto {@link TokenValidationResult}:
 *
 * <ul>
 *   <li>{@code sub} becomes the subject (a token without one is rejected),</li>
 *   <li>{@code scope}/{@code scp} become {@code SCOPE_x} authorities (space-delimited
 *       string or collection),</li>
 *   <li>an {@code authorities} claim is passed through unprefixed, for apps that issue
 *       roles directly into the token.</li>
 * </ul>
 *
 * <p>Signature, expiry and (when configured) issuer checks are owned by the wrapped decoder;
 * anything it rejects is reported as {@link TokenValidationResult#invalid()}.
 */
public class JwtTokenValidationPort implements TokenValidationPort {

    private final JwtDecoder jwtDecoder;

    public JwtTokenValidationPort(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public TokenValidationResult validate(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            return TokenValidationResult.valid(
                    jwt.getSubject(), jwt.getClaims(), resolveAuthorities(jwt), jwt.getExpiresAt());
        } catch (RuntimeException ex) {
            return TokenValidationResult.invalid();
        }
    }

    private static Set<String> resolveAuthorities(Jwt jwt) {
        Set<String> authorities = new LinkedHashSet<>();
        Object scopes = jwt.getClaims().containsKey("scope") ? jwt.getClaims().get("scope")
                : jwt.getClaims().get("scp");
        if (scopes instanceof Collection<?> collection) {
            collection.forEach(scope -> authorities.add("SCOPE_" + scope));
        } else if (scopes instanceof String string) {
            for (String scope : string.split(" ")) {
                if (!scope.isBlank()) {
                    authorities.add("SCOPE_" + scope);
                }
            }
        }
        Object custom = jwt.getClaims().get("authorities");
        if (custom instanceof Collection<?> collection) {
            collection.forEach(authority -> authorities.add(String.valueOf(authority)));
        }
        return authorities;
    }
}
