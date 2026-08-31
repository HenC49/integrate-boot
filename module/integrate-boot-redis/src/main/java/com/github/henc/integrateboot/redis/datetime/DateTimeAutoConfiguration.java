package com.github.henc.integrateboot.redis.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeService;
import com.github.henc.integrateboot.base.util.DateUtils;
import com.github.henc.integrateboot.redis.config.RedisAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationV4;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for the Redis time source: contributes a
 * {@link RedisDateTimeService} whenever a {@link RedisConnectionFactory} is configured
 * (the Redisson starter builds it from {@code spring.data.redis.*}), plus the registrar
 * that connects it — and any user-defined {@link DateTimeService} bean — to the static
 * registry behind {@link DateUtils}.
 *
 * <p>Apps without a Redis connection are unaffected: both beans back off, and
 * {@code DateUtils.getCurrentDateTime()} simply uses another source or the system clock.
 */
@AutoConfiguration(after = {RedissonAutoConfigurationV4.class, RedisAutoConfiguration.class})
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
public class DateTimeAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisDateTimeService.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisDateTimeService redisDateTimeService(StringRedisTemplate stringRedisTemplate) {
        return new RedisDateTimeService(stringRedisTemplate);
    }

    @Bean
    public DateTimeServiceRegistrar redisDateTimeServiceRegistrar(
            ObjectProvider<DateTimeService> services, Environment environment) {
        return new DateTimeServiceRegistrar(services, environment);
    }

}
