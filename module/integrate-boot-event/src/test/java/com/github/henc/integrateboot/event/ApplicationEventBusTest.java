package com.github.henc.integrateboot.event;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEventBusTest {

    private final List<Object> published = new ArrayList<>();
    private final ApplicationEventPublisher publisher = published::add;

    @Test
    void publishDelegatesTheExactPayload() {
        Object event = new Object();

        new ApplicationEventBus(publisher).publish(event);

        assertThat(published).containsExactly(event);
    }

    @Test
    void publishAllDelegatesInDeclarationOrder() {
        Object first = new Object();
        Object second = new Object();

        new ApplicationEventBus(publisher).publishAll(first, second);

        assertThat(published).containsExactly(first, second);
    }
}
