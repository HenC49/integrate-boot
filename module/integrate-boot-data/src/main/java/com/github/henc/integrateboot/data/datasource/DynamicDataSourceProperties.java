package com.github.henc.integrateboot.data.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the optional dynamic (multi) datasource support.
 *
 * <p>Dynamic datasource is backed by MyBatis-Flex's built-in {@code FlexDataSource}. Set
 * {@code integrate-boot.data.datasource.dynamic.enabled=true} to opt in, then declare each
 * datasource under {@code mybatis-flex.datasource.<key>.*}:
 *
 * <pre>{@code
 * integrate-boot:
 *   data:
 *     datasource:
 *       dynamic:
 *         enabled: true
 *
 * mybatis-flex:
 *   datasource:
 *     master:                       # first entry is the default datasource
 *       url: jdbc:mysql://host/db1
 *       username: root
 *       password: secret
 *     slave:
 *       url: jdbc:mysql://host/db2
 *       username: root
 *       password: secret
 * }</pre>
 *
 * <p>Switch datasources with {@code DataSourceKey.use("slave", () -> ...)} or
 * {@code @UseDataSource("slave")}. Defaults to disabled so single-datasource apps are
 * unaffected.
 */
@ConfigurationProperties(prefix = "integrate-boot.data.datasource.dynamic")
public class DynamicDataSourceProperties {

    /**
     * Whether to enable dynamic datasource support. When {@code true}, at least one
     * datasource must be declared under {@code mybatis-flex.datasource.*}.
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
