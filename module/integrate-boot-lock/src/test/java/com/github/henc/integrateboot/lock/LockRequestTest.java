package com.github.henc.integrateboot.lock;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Validation and accessors of {@link LockRequest}: the key is mandatory, durations are
 * optional (negative ones rejected, zero allowed) and left {@code null} so the layer
 * defaults apply.
 */
class LockRequestTest {

    @Test
    void ofCarriesJustTheKey() {
        LockRequest request = LockRequest.of("order:42");

        assertThat(request.key()).isEqualTo("order:42");
        assertThat(request.waitTime()).isNull();
        assertThat(request.leaseTime()).isNull();
    }

    @Test
    void builderCarriesEveryField() {
        LockRequest request = LockRequest.builder()
                .key("order:42")
                .waitTime(Duration.ofSeconds(2))
                .leaseTime(Duration.ofSeconds(30))
                .build();

        assertThat(request.key()).isEqualTo("order:42");
        assertThat(request.waitTime()).isEqualTo(Duration.ofSeconds(2));
        assertThat(request.leaseTime()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void zeroDurationsAreAllowed() {
        LockRequest request = LockRequest.builder()
                .key("k")
                .waitTime(Duration.ZERO)
                .leaseTime(Duration.ZERO)
                .build();

        assertThat(request.waitTime()).isEqualTo(Duration.ZERO);
        assertThat(request.leaseTime()).isEqualTo(Duration.ZERO);
    }

    @Test
    void blankKeyIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LockRequest.of(" "));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LockRequest.builder().build());
    }

    @Test
    void negativeDurationsAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LockRequest.builder().key("k").waitTime(Duration.ofMillis(-1)).build());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LockRequest.builder().key("k").leaseTime(Duration.ofSeconds(-30)).build());
    }
}
