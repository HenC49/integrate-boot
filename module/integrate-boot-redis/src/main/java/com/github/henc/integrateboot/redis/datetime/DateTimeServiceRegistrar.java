package com.github.henc.integrateboot.redis.datetime;

import com.github.henc.integrateboot.base.datetime.DateTimeRegistry;
import com.github.henc.integrateboot.base.datetime.DateTimeService;
import com.github.henc.integrateboot.base.datetime.DateTimeSettings;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Bridges Spring onto the static {@link DateTimeRegistry}: once all singletons exist it
 * registers every {@link DateTimeService} bean (a source is only used after its
 * infrastructure — Redis connection, datasource — is up) and applies the shared
 * {@code integrate-boot.datetime.*} settings (see {@link DateTimeSettings}). On context
 * close it unregisters again, so short-lived contexts (integration tests) do not leak
 * sources into the JVM.
 *
 * <p>Each source module ({@code integrate-boot-redis}, {@code integrate-boot-data})
 * ships its own copy of this registrar so each stays usable standalone; with both
 * modules present both run and converge on the same end state, since registration is
 * idempotent and both see the same beans and settings.
 */
final class DateTimeServiceRegistrar implements SmartInitializingSingleton, DisposableBean {

    private static final Bindable<DateTimeSettings> SETTINGS_BINDABLE = Bindable.of(DateTimeSettings.class);

    private final ObjectProvider<DateTimeService> services;

    private final Environment environment;

    /**
     * Takes the services as a provider so creating this registrar does not force the
     * source beans to instantiate early — they are resolved only once every singleton
     * exists, which is exactly when {@link #afterSingletonsInstantiated()} runs.
     */
    DateTimeServiceRegistrar(ObjectProvider<DateTimeService> services, Environment environment) {
        this.services = services;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<DateTimeService> instantiated = services.orderedStream().toList();
        instantiated.forEach(DateTimeRegistry::register);

        DateTimeSettings settings = Binder.get(environment)
                .bind("integrate-boot.datetime", SETTINGS_BINDABLE)
                .orElse(new DateTimeSettings());
        DateTimeRegistry.setPreferredType(settings.getPrefer());
        DateTimeRegistry.setIntervalEnabled(settings.isIntervalEnabled());
        DateTimeRegistry.setCheckIntervalMillis(settings.getCheckInterval().toMillis());
    }

    @Override
    public void destroy() {
        services.orderedStream().toList().forEach(DateTimeRegistry::unregister);
        // Restore the registry defaults so a closing context does not leave its
        // configuration behind.
        DateTimeRegistry.setPreferredType(null);
        DateTimeRegistry.setIntervalEnabled(true);
        DateTimeRegistry.setCheckIntervalMillis(new DateTimeSettings().getCheckInterval().toMillis());
    }

}
