package com.github.henc.integrateboot.data;

import com.mybatisflex.core.mybatis.FlexConfiguration;
import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Data-layer auto-configuration for integrate-boot.
 *
 * <p>This class only installs <em>conventions and sensible defaults</em>; it deliberately
 * does <strong>not</strong> create a {@link javax.sql.DataSource}, {@link SqlSessionFactory}
 * or transaction manager. Those are provided by Spring Boot's datasource auto-configuration
 * and the MyBatis-Flex starter ({@code SqlSessionFactory}, {@code SqlSessionTemplate}, mapper
 * scanning via {@code @Mapper}, and the {@code FlexTransactionManager}).
 *
 * <p>By importing {@code integrate-boot-data} a service gets:
 * <ul>
 *   <li>underscore-to-camelCase mapping enabled by default (sensible for most schemas);</li>
 *   <li>{@code @Transactional} support out of the box via Flex's transaction manager.</li>
 * </ul>
 * Services still need to provide a datasource (e.g. {@code spring.datasource.*}).
 */
@AutoConfiguration
@ConditionalOnClass({SqlSessionFactory.class, FlexConfiguration.class})
public class DataAutoConfiguration {

    /**
     * Apply data-layer defaults to the auto-configured MyBatis-Flex {@link FlexConfiguration}.
     *
     * <p>Underscore-to-camelCase mapping is turned on so that snake_case columns map to
     * camelCase fields without per-entity configuration. Settings provided explicitly via
     * {@code mybatis-flex.configuration.*} still win, because the MyBatis-Flex starter
     * applies {@code MybatisFlexProperties} after running the customizers.
     */
    @Bean
    ConfigurationCustomizer integrateBootDataConfigurationCustomizer() {
        return configuration -> configuration.setMapUnderscoreToCamelCase(true);
    }
}
