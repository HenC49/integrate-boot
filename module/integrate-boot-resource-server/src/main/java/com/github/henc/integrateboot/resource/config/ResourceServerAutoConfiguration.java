package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.TokenValidationPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Installs the bearer-token filter chain when a {@link TokenValidationPort} is available —
 * either application-provided, or the JWT-backed default from
 * {@link JwtTokenValidationAutoConfiguration} when {@code jwt.jwk-set-uri} is configured.
 */
@AutoConfiguration(after = JwtTokenValidationAutoConfiguration.class)
@ConditionalOnClass({HttpSecurity.class, SecurityFilterChain.class})
@ConditionalOnBean(TokenValidationPort.class)
@EnableConfigurationProperties(ResourceServerProperties.class)
public class ResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint resourceServerAuthenticationEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceServerFilter resourceServerFilter(TokenValidationPort validationPort,
                                                      AuthenticationEntryPoint entryPoint) {
        return new ResourceServerFilter(validationPort, entryPoint);
    }

    @Bean
    @Order(2)
    @ConditionalOnMissingBean(name = "resourceServerSecurityFilterChain")
    public SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http, ResourceServerProperties properties, ResourceServerFilter filter,
            AuthenticationEntryPoint entryPoint) throws Exception {
        String[] permitAllPaths = properties.getPermitAllPaths() == null
                ? new String[0] : properties.getPermitAllPaths().toArray(String[]::new);
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(permitAllPaths).permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
