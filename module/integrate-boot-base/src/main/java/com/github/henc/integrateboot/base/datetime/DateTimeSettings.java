package com.github.henc.integrateboot.base.datetime;

import java.time.Duration;

/**
 * The shared {@code integrate-boot.datetime.*} settings, in a plain-Java shape so every
 * integrate-boot module can bind them (via Spring's {@code Binder}) without one module
 * depending on another. Bound and applied to {@link DateTimeRegistry} by the modules
 * that contribute time sources; non-Spring applications configure the registry directly.
 *
 * <pre>{@code
 * integrate-boot:
 *   datetime:
 *     prefer: redis          # redis | db | server | <custom type>; default: priority order
 *     interval-enabled: true # offset-caching fast path; default true
 *     check-interval: 10m    # how often the offset is refreshed; default 10 minutes
 * }</pre>
 */
public class DateTimeSettings {

    private String prefer;

    private boolean intervalEnabled = true;

    private Duration checkInterval = Duration.ofMinutes(10);

    public String getPrefer() {
        return prefer;
    }

    public void setPrefer(String prefer) {
        this.prefer = prefer;
    }

    public boolean isIntervalEnabled() {
        return intervalEnabled;
    }

    public void setIntervalEnabled(boolean intervalEnabled) {
        this.intervalEnabled = intervalEnabled;
    }

    public Duration getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(Duration checkInterval) {
        this.checkInterval = checkInterval;
    }

}
