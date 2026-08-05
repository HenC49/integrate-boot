package com.github.henc.integrateboot.jackson;

/**
 * Shared constants for the integrate-boot Jackson integration layer.
 */
public final class JacksonConst {

    private JacksonConst() {
    }

    /**
     * Default date/time pattern applied to both {@code java.util.Date} and
     * {@code java.time.LocalDateTime} serialization. Used out of the box — i.e. even when no
     * {@code integrate-boot.jackson.*} properties are configured — so web responses and typed
     * caches share a consistent, human-readable format.
     */
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Default time zone. Jackson 3 defaults to UTC; override to the common East-Asia zone so
     * serialized {@code java.util.Date} values match local expectations without per-app config.
     */
    public static final String DEFAULT_TIME_ZONE = "GMT+8";

    /**
     * Bean name of the standalone {@link tools.jackson.databind.ObjectMapper} that serializes
     * with type information ({@code @class}), intended for Redis / micro-service RPC payloads.
     * It is intentionally <em>not</em> the primary mapper, so web responses stay clean.
     */
    public static final String TYPED_OBJECT_MAPPER = "typedObjectMapper";
}
