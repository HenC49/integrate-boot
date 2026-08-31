package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.JwtTokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring rules for the default JWT-backed {@link TokenValidationPort}: it appears only when
 * {@code integrate-boot.resource-server.jwt.jwk-set-uri} is set, and always loses to an
 * application-provided port.
 */
class JwtTokenValidationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtTokenValidationAutoConfiguration.class));

    @Test
    void defaultJwtPortIsCreatedWhenJwkSetUriConfigured() {
        runner.withPropertyValues("integrate-boot.resource-server.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks")
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenValidationPort.class);
                    assertThat(context).hasSingleBean(JwtTokenValidationPort.class);
                    assertThat(context.getBean(TokenValidationPort.class))
                            .isInstanceOf(JwtTokenValidationPort.class);
                });
    }

    @Test
    void noDefaultPortWithoutJwkSetUri() {
        runner.run(context -> assertThat(context).doesNotHaveBean(TokenValidationPort.class));
    }

    @Test
    void applicationProvidedPortTakesPrecedence() {
        runner.withPropertyValues("integrate-boot.resource-server.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks")
                .withUserConfiguration(CustomPortConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenValidationPort.class);
                    assertThat(context.getBean(TokenValidationPort.class))
                            .isSameAs(context.getBean("appPort", TokenValidationPort.class));
                });
    }

    @Configuration
    static class CustomPortConfiguration {
        @Bean
        TokenValidationPort appPort() {
            return token -> TokenValidationResult.invalid();
        }
    }
}
