package com.github.henc.integrateboot.data.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeService;
import com.github.henc.integrateboot.base.util.DateUtils;
import com.mybatisflex.spring.boot.MultiDataSourceAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Auto-configuration for the database time source: contributes a
 * {@link DbDateTimeService} whenever a {@link DataSource} is configured (Boot's own or
 * MyBatis-Flex's dynamic datasource), plus the registrar that connects it — and any
 * user-defined {@link DateTimeService} bean — to the static registry behind
 * {@link DateUtils}.
 *
 * <p>Apps without a datasource are unaffected: both beans back off, and
 * {@code DateUtils.getCurrentDateTime()} simply uses another source or the system clock.
 */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, MultiDataSourceAutoConfiguration.class})
@ConditionalOnClass({DataSource.class, DbDateTimeService.class})
public class DateTimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DbDateTimeService.class)
    @ConditionalOnBean(DataSource.class)
    public DbDateTimeService dbDateTimeService(DataSource dataSource) {
        return new DbDateTimeService(dataSource);
    }

    @Bean
    public DateTimeServiceRegistrar dataDateTimeServiceRegistrar(
            ObjectProvider<DateTimeService> services, Environment environment) {
        return new DateTimeServiceRegistrar(services, environment);
    }

}
