package com.github.henc.integrateboot.resource;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the JWT-backed default {@link TokenValidationPort}: signature, claims, authorities. */
class JwtTokenValidationPortTest {

    private final KeyPair keyPair = rsaKeyPair();
    private final JwtTokenValidationPort port =
            new JwtTokenValidationPort(decoder(null));

    @Test
    void validTokenYieldsSubjectClaimsScopesAndExpiry() {
        Instant expiry = Instant.now().plusSeconds(60);
        String jwt = signedToken("user-1", "read write", null, expiry);

        TokenValidationResult result = port.validate(jwt);

        assertThat(result.valid()).isTrue();
        assertThat(result.subject()).isEqualTo("user-1");
        // JWT numeric dates carry second precision only.
        assertThat(result.expiresAt()).isEqualTo(expiry.truncatedTo(ChronoUnit.SECONDS));
        assertThat(result.claims()).containsEntry("sub", "user-1");
        assertThat(result.authorities()).containsExactlyInAnyOrder("SCOPE_read", "SCOPE_write");
    }

    @Test
    void authoritiesClaimIsPassedThroughUnprefixed() {
        String jwt = signedToken("user-1", null, List.of("ROLE_admin"), Instant.now().plusSeconds(60));

        assertThat(port.validate(jwt).authorities()).containsExactly("ROLE_admin");
    }

    @Test
    void tamperedTokenIsInvalid() throws Exception {
        String jwt = signedToken("user-1", "read", null, Instant.now().plusSeconds(60));
        // Re-sign the same claims with a different key: right structure, wrong signature.
        KeyPair otherPair = rsaKeyPair();
        SignedJWT parsed = SignedJWT.parse(jwt);
        SignedJWT forged = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), parsed.getJWTClaimsSet());
        forged.sign(new RSASSASigner(otherPair.getPrivate()));

        assertThat(port.validate(forged.serialize()).valid()).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() {
        String jwt = signedToken("user-1", "read", null, Instant.now().minusSeconds(60));

        assertThat(port.validate(jwt).valid()).isFalse();
    }

    @Test
    void garbageTokenIsInvalid() {
        assertThat(port.validate("not-a-jwt").valid()).isFalse();
    }

    @Test
    void tokenWithoutSubjectIsInvalid() {
        String jwt = signedToken(null, "read", null, Instant.now().plusSeconds(60));

        assertThat(port.validate(jwt).valid()).isFalse();
    }

    @Test
    void mismatchedIssuerIsRejectedWhenIssuerConfigured() {
        JwtTokenValidationPort port = new JwtTokenValidationPort(decoder("https://issuer.example.org"));
        String jwt = signedToken("user-1", "read", null, Instant.now().plusSeconds(60));

        assertThat(port.validate(jwt).valid()).isFalse();
    }

    /** Builds the decoder the same way the auto-configuration does. */
    private JwtDecoder decoder(String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) keyPair.getPublic())
                .build();
        if (issuer != null) {
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        }
        return decoder;
    }

    private String signedToken(String subject, String scope, List<String> authorities, Instant expiry) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .expirationTime(Date.from(expiry))
                    .issueTime(Date.from(Instant.now().minusSeconds(5)));
            if (subject != null) {
                claims.subject(subject);
            }
            if (scope != null) {
                claims.claim("scope", scope);
            }
            if (authorities != null) {
                claims.claim("authorities", authorities);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner(keyPair.getPrivate()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
