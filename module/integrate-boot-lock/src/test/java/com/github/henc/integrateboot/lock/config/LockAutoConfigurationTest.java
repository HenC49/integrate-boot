package com.github.henc.integrateboot.lock.config;

import com.github.henc.integrateboot.lock.DefaultDistributedLock;
import com.github.henc.integrateboot.lock.DistributedLock;
import com.github.henc.integrateboot.lock.LockHandle;
import com.github.henc.integrateboot.lock.LockProvider;
import com.github.henc.integrateboot.lock.LockRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Activation rules of {@link LockAutoConfiguration}: a {@link LockProvider} bean switches
 * the default facade on, a custom {@link DistributedLock} bean replaces it, and the
 * property defaults reach the facade.
 */
class LockAutoConfigurationTest {

    /**
     * Recording stand-in for a backend (the Redis provider in production): hands out
     * locks against a held-set and remembers every request it received, so the prefix
     * and duration defaults the facade applies are assertable.
     */
    static class RecordingLockProvider implements LockProvider {

        final Set<String> held = ConcurrentHashMap.newKeySet();

        final List<LockRequest> received = new CopyOnWriteArrayList<>();

        @Override
        public Optional<LockHandle> tryAcquire(LockRequest request) {
            received.add(request);
            if (!held.add(request.key())) {
                return Optional.empty();
            }
            return Optional.of(new LockHandle() {
                @Override
                public String key() {
                    return request.key();
                }

                @Override
                public void unlock() {
                    held.remove(request.key());
                }
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProviderConfig {

        @Bean
        RecordingLockProvider recordingLockProvider() {
            return new RecordingLockProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomFacadeConfig {

        // Held in a field so the test can compare identity: with proxyBeanMethods=false
        // each @Bean method call would otherwise create a fresh instance.
        private final DistributedLock facade = new DistributedLock() {
            @Override
            public <T> T execute(LockRequest request, java.util.function.Supplier<T> action) {
                return action.get();
            }

            @Override
            public LockHandle acquire(LockRequest request) {
                throw new UnsupportedOperationException();
            }
        };

        @Bean
        DistributedLock customDistributedLock() {
            return facade;
        }
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LockAutoConfiguration.class));
    }

    @Test
    void noFacadeWithoutAProviderBean() {
        runner().run(context -> assertThat(context).doesNotHaveBean(DistributedLock.class));
    }

    @Test
    void providerBeanActivatesTheDefaultFacadeWithTheDefaultSettings() {
        runner().withUserConfiguration(ProviderConfig.class).run(context -> {
            assertThat(context).hasSingleBean(DistributedLock.class);
            assertThat(context.getBean(DistributedLock.class)).isInstanceOf(DefaultDistributedLock.class);

            // End to end through the auto-configured bean: the business key lands at the
            // provider namespaced with the built-in prefix, with the fail-fast default wait.
            context.getBean(DistributedLock.class).execute("order:42", () -> "ok");
            RecordingLockProvider provider = context.getBean(RecordingLockProvider.class);
            assertThat(provider.received.get(0).key()).isEqualTo("integrate-boot:lock:order:42");
            assertThat(provider.received.get(0).waitTime()).isEqualTo(Duration.ZERO);
            assertThat(provider.received.get(0).leaseTime()).isNull();
        });
    }

    @Test
    void propertiesOverrideThePrefixAndDefaults() {
        runner()
                .withPropertyValues(
                        "integrate-boot.lock.key-prefix=myapp:lock:",
                        "integrate-boot.lock.default-wait-time=2s",
                        "integrate-boot.lock.default-lease-time=30s")
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    context.getBean(DistributedLock.class).execute("order:42", () -> "ok");

                    RecordingLockProvider provider = context.getBean(RecordingLockProvider.class);
                    assertThat(provider.received.get(0).key()).isEqualTo("myapp:lock:order:42");
                    assertThat(provider.received.get(0).waitTime()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(provider.received.get(0).leaseTime()).isEqualTo(Duration.ofSeconds(30));
                });
    }

    @Test
    void customFacadeBeanMakesTheDefaultBackOff() {
        runner()
                .withUserConfiguration(ProviderConfig.class, CustomFacadeConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DistributedLock.class);
                    assertThat(context.getBean(DistributedLock.class))
                            .isSameAs(context.getBean(CustomFacadeConfig.class).customDistributedLock());
                });
    }
}
