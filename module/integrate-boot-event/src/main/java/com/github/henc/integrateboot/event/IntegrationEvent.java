package com.github.henc.integrateboot.event;

/**
 * Marker interface for cross-module <em>integration</em> events — events whose publisher
 * deliberately announces a business fact to any module that cares (as opposed to events
 * consumed by a single, known listener).
 *
 * <p>Purely semantic today: carrying the marker documents intent and is the selection
 * criterion the planned distributed bridge (Redis pub/sub via Redisson) will use to
 * decide which local events are broadcast cluster-wide.
 */
public interface IntegrationEvent {
}
