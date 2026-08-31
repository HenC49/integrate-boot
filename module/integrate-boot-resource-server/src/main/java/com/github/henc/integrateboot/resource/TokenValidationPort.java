package com.github.henc.integrateboot.resource;

/**
 * Application port used to validate bearer access tokens.
 *
 * <p>The token passed to this method never contains the {@code Bearer } prefix. Implementations
 * own the token verification policy, including signature, issuer, audience and expiry checks.
 */
@FunctionalInterface
public interface TokenValidationPort {

    TokenValidationResult validate(String token);
}
