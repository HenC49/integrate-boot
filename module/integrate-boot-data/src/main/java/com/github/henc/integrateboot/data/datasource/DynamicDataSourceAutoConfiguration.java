package com.github.henc.integrateboot.data.datasource;

import com.mybatisflex.core.datasource.DataSourceKey;
import com.mybatisflex.core.datasource.FlexDataSource;
import com.mybatisflex.spring.boot.MultiDataSourceAutoConfiguration;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Auto-configuration that guards MyBatis-Flex's built-in dynamic datasource support behind
 * an explicit yaml switch.
 *
 * <p>MyBatis-Flex activates multi-datasource automatically when any
 * {@code mybatis-flex.datasource.*} property is present. This class adds an opt-in flag
 * ({@code integrate-boot.data.datasource.dynamic.enabled}) and fails fast with a clear
 * message if the flag is on but no datasource is configured.
 *
 * <p>It does <strong>not</strong> create the datasource bean itself —
 * {@link MultiDataSourceAutoConfiguration} does. This class only runs before it to validate.
 */
@AutoConfiguration(before = MultiDataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "integrate-boot.data.datasource.dynamic", name = "enabled", havingValue = "true")
@ConditionalOnClass({FlexDataSource.class, DataSourceKey.class})
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
public class DynamicDataSourceAutoConfiguration {

    // The exact value type does not matter here — we only check presence. Using a raw Map
    // avoids fragile nested generics while still aggregating yaml entries like the starter.
    @SuppressWarnings("rawtypes")
    private static final Bindable<Map> DATASOURCE_BINDABLE = Bindable.of(Map.class);

    private final Environment environment;

    public DynamicDataSourceAutoConfiguration(Environment environment) {
        this.environment = environment;
    }

    /**
     * Fail fast if dynamic datasource is enabled but no {@code mybatis-flex.datasource.*}
     * entry is configured, otherwise the app would silently fall back to a single
     * datasource — the opposite of what the user asked for.
     */
    @PostConstruct
    void validateDatasourceConfigured() {
        // Bind mybatis-flex.datasource into a Map the same way MybatisFlexProperties does.
        // Environment.getProperty() does not aggregate map properties, so Binder is required.
        Map<?, ?> datasource = Binder.get(environment)
                .bind("mybatis-flex.datasource", DATASOURCE_BINDABLE)
                .orElse(null);
        if (datasource == null || datasource.isEmpty()) {
            throw new IllegalStateException(
                    "Dynamic datasource is enabled (integrate-boot.data.datasource.dynamic.enabled=true) "
                            + "but no datasource is configured. Declare at least one datasource under "
                            + "'mybatis-flex.datasource.<key>.*'.");
        }
    }
}
