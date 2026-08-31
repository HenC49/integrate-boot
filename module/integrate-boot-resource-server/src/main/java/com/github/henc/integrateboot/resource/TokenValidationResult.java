package com.github.henc.integrateboot.resource;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable result returned by {@link TokenValidationPort}. */
public record TokenValidationResult(
        boolean valid,
        String subject,
        Map<String, Object> claims,
        Set<String> authorities,
        Instant expiresAt) {

    public TokenValidationResult {
        claims = claims == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(claims));
        authorities = authorities == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(authorities));
        if (valid && (subject == null || subject.isBlank())) {
            throw new IllegalArgumentException("A valid token must have a subject");
        }
        if (!valid) {
            subject = null;
            expiresAt = null;
        }
    }

    public static TokenValidationResult valid(
            String subject, Map<String, Object> claims, Set<String> authorities, Instant expiresAt) {
        return new TokenValidationResult(true, subject, claims, authorities, expiresAt);
    }

    public static TokenValidationResult invalid() {
        return new TokenValidationResult(false, null, Map.of(), Set.of(), null);
    }
}
