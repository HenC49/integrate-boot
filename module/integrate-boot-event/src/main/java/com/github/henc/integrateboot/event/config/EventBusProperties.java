package com.github.henc.integrateboot.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the event module. The switches are primarily consumed by the auto-configuration's
 * {@code @ConditionalOnProperty} guards; this class binds them for IDE metadata (yaml completion)
 * and future programmatic use.
 */
@ConfigurationProperties("integrate-boot.event")
public class EventBusProperties {

    /** Master switch; on by default — the core is a plain in-process wrapper with no external dependencies. */
    private boolean enabled = true;

    private final Async async = new Async();

    private final Reliability reliability = new Reliability();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Async getAsync() { return async; }
    public Reliability getReliability() { return reliability; }

    public static class Async {

        /**
         * Enables the module's unified {@code @EnableAsync} takeover (default on), so
         * applications never need their own async configuration. Async listeners then run
         * on Boot's {@code applicationTaskExecutor}.
         */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Reliability {

        /**
         * Enables the glue for Spring Modulith's event publication registry: a transactional
         * outbox that re-delivers {@code @TransactionalEventListener} deliveries that did not
         * complete. Off by default and inert unless the Modulith artifacts are on the
         * classpath (they are never pulled in transitively).
         */
        private boolean enabled = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
