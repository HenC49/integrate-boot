package com.github.henc.integrateboot.auth.config;

import com.github.henc.integrateboot.auth.AuthConst;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the integrate-boot authentication layer.
 *
 * <p>The OAuth2 clients, issuer and JWK set are configured through Spring Boot's native
 * {@code spring.security.oauth2.authorization-server.*} properties (handled by the Spring Boot
 * authorization-server starter). This class only governs the integrate-boot-specific extras.
 *
 * <pre>{@code
 * integrate-boot:
 *   auth:
 *     password-grant-enabled: true
 *     permit-all-paths:
 *       - /public/**
 * }</pre>
 */
@ConfigurationProperties(prefix = "integrate-boot.auth")
public class AuthenticationProperties {

    /**
     * Whether the custom {@code password} grant type is enabled. When {@code true} (the default),
     * {@code grant_type=password} is accepted at the token endpoint and authenticates the user
     * through the configured {@link org.springframework.security.core.userdetails.UserDetailsService}.
     */
    private boolean passwordGrantEnabled = true;

    /**
     * Additional request paths to permit without authentication, on top of the OAuth2 / actuator
     * defaults from {@link AuthConst#DEFAULT_PERMIT_ALL_PATHS}.
     */
    private List<String> permitAllPaths = new ArrayList<>();

    public boolean isPasswordGrantEnabled() {
        return passwordGrantEnabled;
    }

    public void setPasswordGrantEnabled(boolean passwordGrantEnabled) {
        this.passwordGrantEnabled = passwordGrantEnabled;
    }

    public List<String> getPermitAllPaths() {
        return permitAllPaths;
    }

    public void setPermitAllPaths(List<String> permitAllPaths) {
        this.permitAllPaths = permitAllPaths;
    }
}
