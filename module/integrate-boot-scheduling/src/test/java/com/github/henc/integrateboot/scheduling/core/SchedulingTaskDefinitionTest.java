package com.github.henc.integrateboot.scheduling.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SchedulingTaskDefinitionTest {

    @Test
    void defaultsTimeZoneWhenNotProvided() {
        SchedulingTaskDefinition definition = new SchedulingTaskDefinition(
                "orders", "0/5 * * * * ?", 2, true, true, null);

        assertThat(definition.timeZone()).isNotNull();
    }

    @Test
    void rejectsInvalidTaskDefinition() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SchedulingTaskDefinition("", "* * * * * ?", 1, false, false, null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SchedulingTaskDefinition("orders", "* * * * * ?", 0, false, false, null));
    }
}
