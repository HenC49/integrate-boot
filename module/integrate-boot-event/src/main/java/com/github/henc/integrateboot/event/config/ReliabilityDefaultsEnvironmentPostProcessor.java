package com.github.henc.integrateboot.event.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contributes the recommended Modulith defaults when the reliability layer is switched on
 * ({@code integrate-boot.event.reliability.enabled=true}). The property source is appended
 * with the lowest precedence, so any explicit {@code spring.modulith.*} setting wins.
 */
public class ReliabilityDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String ENABLED_PROPERTY = "integrate-boot.event.reliability.enabled";
    static final String PROPERTY_SOURCE_NAME = "integrateBootEventReliabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!"true".equalsIgnoreCase(environment.getProperty(ENABLED_PROPERTY))) {
            return;
        }
        Map<String, Object> defaults = new LinkedHashMap<>();
        // Bootstrap the registry table when it does not exist yet; Modulith backs off
        // automatically when the table was already created by Flyway/Liquibase.
        defaults.put("spring.modulith.events.jdbc.schema-initialization.enabled", "true");
        // Re-deliver publications left incomplete by a crash or a failing listener on restart.
        defaults.put("spring.modulith.events.republish-outstanding-events-on-restart", "true");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }
}
