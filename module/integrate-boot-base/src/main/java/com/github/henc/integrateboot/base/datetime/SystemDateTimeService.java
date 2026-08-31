package com.github.henc.integrateboot.base.datetime;

import java.util.Date;

/**
 * The {@code "server"} time source: the local system clock. It never fails, so the
 * registry keeps it as the built-in last resort even when it is not registered —
 * {@link DateTimeRegistry} always answers with the system clock when no registered
 * source is available.
 */
public class SystemDateTimeService implements DateTimeService {

    /** Source identifier: {@value}. */
    public static final String TYPE = DateTimeRegistry.TYPE_SERVER;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public int getOrder() {
        // Outranked by every registered source; only used as the last fallback.
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean useInterval() {
        // Local reads are already free — offset caching would buy nothing.
        return false;
    }

    @Override
    public Date getCurrentDate() {
        return new Date();
    }

}
