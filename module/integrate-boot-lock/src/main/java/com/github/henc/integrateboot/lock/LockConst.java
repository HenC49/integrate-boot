package com.github.henc.integrateboot.lock;

/**
 * Shared constants for the integrate-boot distributed lock layer.
 *
 * <p>These values back the defaults applied by {@code LockProperties}: they namespace the
 * keys handed to a {@link LockProvider} so lock keys cannot collide with other data kept
 * in the same backend.
 */
public final class LockConst {

    private LockConst() {
    }

    /**
     * Default prefix prepended to every business key before it reaches a
     * {@link LockProvider} (so a business key like {@code order:42} becomes
     * {@code integrate-boot:lock:order:42} in the backend). Configurable (and disableable
     * with an empty value) through {@code integrate-boot.lock.key-prefix}.
     */
    public static final String DEFAULT_KEY_PREFIX = "integrate-boot:lock:";
}
