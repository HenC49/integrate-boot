package com.github.henc.test.event;

import com.github.henc.integrateboot.event.IntegrationEvent;

/**
 * Announced whenever a user account is created — the sample app's integration event.
 * Any module may react to it without the user domain knowing who they are.
 */
public record UserCreated(Long id, String userName) implements IntegrationEvent {
}
