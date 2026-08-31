package com.github.henc.integrateboot.resource;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenValidationResultTest {

    @Test
    void copiesClaimsAndAuthorities() {
        Map<String, Object> claims = new java.util.HashMap<>();
        Set<String> authorities = new java.util.HashSet<>();
        TokenValidationResult result = TokenValidationResult.valid(
                "user-1", claims, authorities, Instant.now());

        claims.put("role", "ADMIN");
        authorities.add("ROLE_ADMIN");

        assertThat(result.claims()).isEmpty();
        assertThat(result.authorities()).isEmpty();
    }

    @Test
    void rejectsValidResultWithoutSubject() {
        assertThatThrownBy(() -> TokenValidationResult.valid(" ", Map.of(), Set.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidResultHasNoIdentity() {
        TokenValidationResult result = TokenValidationResult.invalid();

        assertThat(result.valid()).isFalse();
        assertThat(result.subject()).isNull();
        assertThat(result.expiresAt()).isNull();
    }
}
