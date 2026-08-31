package com.github.henc.integrateboot.data.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeRegistry;
import com.github.henc.integrateboot.base.datetime.DateTimeService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-level tests: the database time source is contributed when a datasource
 * exists, honors user overrides, is bridged into the static {@link DateTimeRegistry}
 * (and removed again when the context closes), and applies the shared
 * {@code integrate-boot.datetime.*} settings.
 */
class DateTimeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DateTimeAutoConfiguration.class));

    @Test
    void backsOffWithoutDataSource() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DbDateTimeService.class);
            // The registrar still exists — it sweeps up any user-defined source beans.
            assertThat(context).hasBean("dataDateTimeServiceRegistrar");
        });
    }

    @Test
    void contributesAndRegistersSourceWithDataSource() {
        runner.withBean("dataSource", DataSource.class, () -> embeddedDataSource())
                .run(context -> {
                    assertThat(context).hasSingleBean(DbDateTimeService.class);
                    assertThat(DateTimeRegistry.getRegisteredServices())
                            .anySatisfy(service -> assertThat(service.getType()).isEqualTo("db"));
                });

        // Context closed — the source must be unregistered again.
        assertThat(DateTimeRegistry.getRegisteredServices())
                .noneMatch(service -> "db".equals(service.getType()));
    }

    @Test
    void userDefinedSourceReplacesTheDefault() {
        DbDateTimeService custom = new DbDateTimeService(embeddedDataSource(), "select now()");

        runner.withBean("dataSource", DataSource.class, this::embeddedDataSource)
                .withBean("customDbDateTimeService", DbDateTimeService.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(DbDateTimeService.class);
                    assertThat(context.getBean(DbDateTimeService.class)).isSameAs(custom);
                });
    }

    @Test
    void customSourceBeansAreRegisteredToo() {
        DateTimeService custom = new DateTimeService() {
            @Override
            public String getType() {
                return "satellite";
            }

            @Override
            public java.util.Date getCurrentDate() {
                return new java.util.Date();
            }
        };

        runner.withBean("satelliteService", DateTimeService.class, () -> custom)
                .run(context -> assertThat(DateTimeRegistry.getRegisteredServices()).contains(custom));
    }

    @Test
    void sharedDatetimeSettingsAreApplied() {
        runner.withPropertyValues(
                        "integrate-boot.datetime.prefer=db",
                        "integrate-boot.datetime.interval-enabled=false",
                        "integrate-boot.datetime.check-interval=30s")
                .run(context -> {
                    assertThat(DateTimeRegistry.getPreferredType()).isEqualTo("db");
                    assertThat(DateTimeRegistry.isIntervalEnabled()).isFalse();
                    assertThat(DateTimeRegistry.getCheckIntervalMillis()).isEqualTo(30_000L);
                });

        // Defaults restored on context close.
        assertThat(DateTimeRegistry.getPreferredType()).isNull();
        assertThat(DateTimeRegistry.isIntervalEnabled()).isTrue();
        assertThat(DateTimeRegistry.getCheckIntervalMillis()).isEqualTo(600_000L);
    }

    private DataSource embeddedDataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
    }
}
