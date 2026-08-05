package com.github.henc.integrateboot.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.henc.integrateboot.cache.CacheConst;
import com.github.henc.integrateboot.cache.CacheProperties;
import com.github.henc.integrateboot.cache.CacheProperties.Spec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Local cache auto-configuration for integrate-boot.
 *
 * <p>Registers two {@link CaffeineCacheManager Caffeine-backed cache managers} out of the box,
 * both available by name for {@code @Cacheable(cacheManager = "...")} and friends:
 *
 * <ul>
 *   <li>{@link CacheConst#MANAGER_PERMANENT cacheManagerPermanent} — a permanent cache (no
 *       time-based expiry); entries are only evicted by size or cleared explicitly.</li>
 *   <li>{@link CacheConst#MANAGER_EXPIRING cacheManagerExpiring} ({@code @Primary}) — a cache
 *       whose entries expire {@code expire-after-write} (2 minutes by default). This is the
 *       manager that plain {@code @Cacheable} resolves to when no manager is named.</li>
 * </ul>
 *
 * <p>Apps wanting to use declarative caching ({@code @Cacheable} / {@code @CacheEvict} / ...)
 * still need to add {@code @EnableCaching} once — auto-configuring the cache managers does not
 * turn the Spring Cache abstraction on by itself. The managers can also be used directly:
 *
 * <pre>{@code
 * @Autowired
 * @Qualifier("cacheManagerPermanent")
 * private CacheManager cacheManager;
 *
 * cacheManager.getCache("dictionaries").put(key, value);
 * }</pre>
 *
 * <p>Both beans are guarded by {@code @ConditionalOnMissingBean}, so an application can replace
 * either manager with its own definition without conflicting with the defaults.
 */
@AutoConfiguration
@ConditionalOnClass({CacheManager.class, Caffeine.class})
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

    /**
     * Permanent (no-expiry) local cache manager.
     *
     * <p>Entries are bounded only by {@code maximum-size}; use for reference data that should
     * live until evicted by size or cleared explicitly.
     */
    @Bean(CacheConst.MANAGER_PERMANENT)
    @ConditionalOnMissingBean(name = CacheConst.MANAGER_PERMANENT)
    public CaffeineCacheManager cacheManagerPermanent(CacheProperties properties) {
        return build(properties, properties.getPermanent());
    }

    /**
     * Time-to-live local cache manager — the {@code @Primary} manager resolved by plain
     * {@code @Cacheable}. Entries expire {@code expire-after-write} (2 minutes by default).
     */
    @Bean(CacheConst.MANAGER_EXPIRING)
    @Primary
    @ConditionalOnMissingBean(name = CacheConst.MANAGER_EXPIRING)
    public CaffeineCacheManager cacheManagerExpiring(CacheProperties properties) {
        return build(properties, properties.getExpiring());
    }

    private CaffeineCacheManager build(CacheProperties properties, Spec spec) {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setAllowNullValues(properties.isCacheNullValues());
        manager.setCaffeine(caffeine(spec));
        return manager;
    }

    private Caffeine<Object, Object> caffeine(Spec spec) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder();
        builder.initialCapacity(spec.getInitialCapacity());
        builder.maximumSize(spec.getMaximumSize());
        // Only apply the expiry policies that were actually configured, so a null duration
        // means "no expiry" rather than being interpreted by Caffeine as zero.
        if (spec.getExpireAfterAccess() != null) {
            builder.expireAfterAccess(spec.getExpireAfterAccess());
        }
        if (spec.getExpireAfterWrite() != null) {
            builder.expireAfterWrite(spec.getExpireAfterWrite());
        }
        return builder;
    }
}
