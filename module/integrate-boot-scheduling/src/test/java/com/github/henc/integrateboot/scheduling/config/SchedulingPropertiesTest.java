package com.github.henc.integrateboot.scheduling.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingPropertiesTest {

    @Test
    void exposesSafeDefaults() {
        SchedulingProperties properties = new SchedulingProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getExecutor().isEnabled()).isFalse();
        assertThat(properties.getAdmin().isEnabled()).isFalse();
        assertThat(properties.getAdmin().getBasePath()).isEqualTo("/integrate/scheduling");
    }
}
