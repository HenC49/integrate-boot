package com.github.henc.integrateboot.auth.user;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Contract for loading user details during a password-grant login. This is the extension point
 * business applications implement to plug in their user store (database, LDAP, external service,
 * etc.).
 *
 * <p>It extends Spring Security's {@link UserDetailsService} so the same implementation is reused
 * by both the custom password grant and any other Spring Security flow that needs to resolve a
 * user by username. Implementations are expected to be backed by the application's data layer.
 */
public interface UserDetailsPasswordService extends UserDetailsService {
}
