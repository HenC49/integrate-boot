package com.github.henc.integrateboot.starter;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience annotation for business services built on integrate-boot.
 *
 * <p>Annotating the main class with {@code @IntegrateBoot} enables Spring Boot
 * auto-configuration and component-scans the <em>conventional</em> layer packages — any
 * package named {@code controller}, {@code service}, {@code service.impl} or {@code domain},
 * at any depth under the application. A service therefore does not need to live under a
 * fixed root package; it only needs to follow the layering conventions:
 *
 * <table>
 *   <caption>Bean location conventions</caption>
 *   <tr><th>Layer</th><th>Package convention</th><th>Example</th></tr>
 *   <tr><td>Controller</td><td>{@code **.controller.**}</td><td>{@code xxx.controller.XxxController}</td></tr>
 *   <tr><td>Service interface</td><td>{@code **.service.**}</td><td>{@code xxx.service.XxxService}</td></tr>
 *   <tr><td>Service implementation</td><td>{@code **.service.impl.**}</td><td>{@code xxx.service.impl.XxxServiceImpl}</td></tr>
 *   <tr><td>Repository</td><td>{@code **.domain.**}</td><td>{@code xxx.domain.XxxRepository}</td></tr>
 * </table>
 *
 * <p>Typical usage:
 * <pre>{@code
 * @IntegrateBoot
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * <p>Callers may still disable specific auto-configurations through {@link #exclude} /
 * {@link #excludeName}. To scan additional packages beyond the conventions above, add a
 * regular {@code @ComponentScan} next to {@code @IntegrateBoot} on the main class, or fall
 * back to {@code @SpringBootApplication} instead.
 *
 * <p>Transitively pulling in {@code integrate-boot-starter} also brings the
 * {@code integrate-boot-data} module, so the data layer (MyBatis-Flex, transactions,
 * underscore-to-camelCase mapping) is wired up automatically. The
 * {@code integrate-boot-cache} module ships two Caffeine-backed cache managers
 * ({@code cacheManagerPermanent} and the {@code @Primary cacheManagerExpiring}); since
 * {@code @IntegrateBoot} is meta-annotated with {@link EnableCaching}, declarative caching
 * ({@code @Cacheable} / {@code @CacheEvict} / ...) works out of the box — no extra
 * {@code @EnableCaching} needed on the application class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableCaching
@ComponentScan(
        basePackages = {
                "**.controller.**",
                "**.service.**",
                "**.service.impl.**",
                "**.domain.**"
        }
)
public @interface IntegrateBoot {

    /**
     * Auto-configuration classes to exclude.
     */
    @org.springframework.core.annotation.AliasFor(
            annotation = EnableAutoConfiguration.class, attribute = "exclude")
    Class<?>[] exclude() default {};

    /**
     * Auto-configuration class names to exclude.
     */
    @org.springframework.core.annotation.AliasFor(
            annotation = EnableAutoConfiguration.class, attribute = "excludeName")
    String[] excludeName() default {};

    /**
     * Whether to proxy {@code @Bean} methods (CGLIB). Defaults to {@code true}, matching Spring.
     */
    @org.springframework.core.annotation.AliasFor(
            annotation = SpringBootConfiguration.class, attribute = "proxyBeanMethods")
    boolean proxyBeanMethods() default true;
}
