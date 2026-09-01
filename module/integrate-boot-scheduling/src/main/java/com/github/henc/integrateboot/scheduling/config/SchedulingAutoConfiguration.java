package com.github.henc.integrateboot.scheduling.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import com.github.henc.integrateboot.scheduling.executor.RegisteredSchedulingJobHandler;
import com.github.henc.integrateboot.scheduling.executor.SchedulingTaskHandlerRegistry;
import com.github.henc.integrateboot.scheduling.admin.InMemorySchedulingAdminService;
import com.github.henc.integrateboot.scheduling.admin.SchedulingAdminController;
import com.github.henc.integrateboot.scheduling.admin.SchedulingAdminService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(XxlJobSpringExecutor.class)
@ConditionalOnProperty(prefix = "integrate-boot.scheduling", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SchedulingTaskHandlerRegistry schedulingTaskHandlerRegistry(
            java.util.Map<String, com.github.henc.integrateboot.scheduling.core.SchedulingTaskHandler> handlers) {
        return new SchedulingTaskHandlerRegistry(handlers);
    }

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "integrate-boot.scheduling.executor", name = "enabled", havingValue = "true")
    XxlJobSpringExecutor xxlJobExecutor(SchedulingProperties properties,
                                         SchedulingTaskHandlerRegistry registry) {
        SchedulingProperties.Executor executor = properties.getExecutor();
        require(executor.getAdminAddresses(), "integrate-boot.scheduling.executor.admin-addresses");
        require(executor.getAppName(), "integrate-boot.scheduling.executor.app-name");
        if (executor.getPort() < 0 || executor.getPort() > 65535) {
            throw new IllegalArgumentException("integrate-boot.scheduling.executor.port must be between 0 and 65535");
        }
        XxlJobSpringExecutor bean = new XxlJobSpringExecutor();
        bean.setAdminAddresses(executor.getAdminAddresses());
        bean.setAppname(executor.getAppName());
        bean.setAccessToken(executor.getAccessToken());
        bean.setAddress(executor.getAddress());
        bean.setIp(executor.getIp());
        bean.setPort(executor.getPort());
        bean.setLogPath(executor.getLogPath());
        bean.setLogRetentionDays(executor.getLogRetentionDays());
        registry.getHandlers().forEach((taskId, handler) ->
                bean.registryJobHandler(taskId, new RegisteredSchedulingJobHandler(taskId, handler)));
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "integrate-boot.scheduling.admin", name = "enabled", havingValue = "true")
    SchedulingAdminService schedulingAdminService() {
        return new InMemorySchedulingAdminService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "integrate-boot.scheduling.admin", name = "enabled", havingValue = "true")
    SchedulingAdminController schedulingAdminController(SchedulingAdminService service) {
        return new SchedulingAdminController(service);
    }

    private static void require(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when XXL-JOB executor is enabled");
        }
    }
}
