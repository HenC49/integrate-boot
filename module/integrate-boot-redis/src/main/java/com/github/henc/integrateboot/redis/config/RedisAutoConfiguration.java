package com.github.henc.integrateboot.redis.config;

import com.github.henc.integrateboot.jackson.JacksonConst;
import com.github.henc.integrateboot.redis.RedisConst;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Redis auto-configuration for integrate-boot.
 *
 * <p>Sits on top of the Redisson Spring Boot starter, which already auto-configures a
 * {@link RedissonClient} and a {@link RedissonConnectionFactory} from Spring Boot's native
 * {@code spring.data.redis.*} properties. Because both {@link RedisTemplate} and
 * {@link RedissonClient} are backed by that single connection factory, they share the same
 * underlying Redis client and configuration — nothing extra is needed to keep them in sync.
 *
 * <p>This class contributes two things:
 * <ol>
 *   <li><b>Default serializers.</b> Replaces the starter's bare {@link RedisTemplate} with one
 *       using {@link org.springframework.data.redis.serializer.StringRedisSerializer} for keys /
 *       hash-keys and a JSON serializer (backed by the {@code typedObjectMapper}) for values /
 *       hash-values, so cached objects are stored as readable JSON with type information and the
 *       configured date/time format. The {@code @ConditionalOnMissingBean} guards mean a
 *       user-defined template of the same name always wins.</li>
 *   <li><b>Extra instances.</b> For every entry under {@code integrate-boot.redis.multi.*},
 *       registers a dedicated {@code redissonClient-<name>}, {@code redisTemplate-<name>} and
 *       {@code stringRedisTemplate-<name>} as named singletons, injectable via
 *       {@code @Qualifier}.</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass({RedissonClient.class, RedisTemplate.class})
@EnableConfigurationProperties(RedisProperties.class)
public class RedisAutoConfiguration {

    /**
     * Default {@link RedisTemplate} — {@code @Primary} so plain {@code @Autowired
     * RedisTemplate} resolves to it. Keys use a {@link org.springframework.data.redis.serializer.StringRedisSerializer};
     * values are serialized through the shared {@code typedObjectMapper} (Jackson 3, with type
     * information) so objects are stored as JSON carrying their concrete type.
     */
    @Bean(RedisConst.REDIS_TEMPLATE)
    @Primary
    @ConditionalOnMissingBean(name = RedisConst.REDIS_TEMPLATE)
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       @Qualifier(JacksonConst.TYPED_OBJECT_MAPPER)
                                                       ObjectMapper typedObjectMapper) {
        return RedisSerializers.buildTemplate(connectionFactory, typedObjectMapper);
    }

    /**
     * Default {@link StringRedisTemplate} — also {@code @Primary} for symmetry.
     */
    @Bean(RedisConst.STRING_REDIS_TEMPLATE)
    @Primary
    @ConditionalOnMissingBean(name = RedisConst.STRING_REDIS_TEMPLATE)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return RedisSerializers.buildStringTemplate(connectionFactory);
    }

    /**
     * Registers beans for each extra Redis instance declared under
     * {@code integrate-boot.redis.multi.*}, once the singleton registry is populated. With an
     * empty {@code multi} map (the default) this is a no-op, i.e. single-Redis mode.
     */
    @Bean
    public MultiRedisRegistrar multiRedisRegistrar(RedisProperties properties,
                                                   ConfigurableListableBeanFactory beanFactory,
                                                   @Qualifier(JacksonConst.TYPED_OBJECT_MAPPER)
                                                   ObjectMapper typedObjectMapper) {
        return new MultiRedisRegistrar(properties, beanFactory, typedObjectMapper);
    }

    /**
     * Backs {@link #multiRedisRegistrar} — runs after all singletons are created so
     * {@link RedisProperties} is fully bound before any extra clients are built.
     */
    static class MultiRedisRegistrar implements SmartInitializingSingleton {

        private final RedisProperties properties;

        private final ConfigurableListableBeanFactory beanFactory;

        private final ObjectMapper typedObjectMapper;

        MultiRedisRegistrar(RedisProperties properties, ConfigurableListableBeanFactory beanFactory,
                            ObjectMapper typedObjectMapper) {
            this.properties = properties;
            this.beanFactory = beanFactory;
            this.typedObjectMapper = typedObjectMapper;
        }

        @Override
        public void afterSingletonsInstantiated() {
            Map<String, RedissonConfig> multi = properties.getMulti();
            if (multi == null || multi.isEmpty()) {
                return;
            }
            for (Map.Entry<String, RedissonConfig> entry : multi.entrySet()) {
                registerInstance(entry.getKey(), entry.getValue());
            }
        }

        private void registerInstance(String name, RedissonConfig config) {
            RedissonClient client = Redisson.create(config.toRedissonConfig());
            RedissonConnectionFactory connectionFactory = new RedissonConnectionFactory(client);

            beanFactory.registerSingleton(RedisConst.REDISSON_CLIENT_PREFIX + name, client);
            beanFactory.registerSingleton(
                    RedisConst.REDIS_TEMPLATE_PREFIX + name,
                    RedisSerializers.buildTemplate(connectionFactory, typedObjectMapper));
            beanFactory.registerSingleton(
                    RedisConst.STRING_REDIS_TEMPLATE_PREFIX + name,
                    RedisSerializers.buildStringTemplate(connectionFactory));
        }
    }
}
