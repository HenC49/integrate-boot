package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.JwtTokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * Provides the default JWT-backed {@link TokenValidationPort} when the application points it
 * at an authorization server's JWKS endpoint:
 *
 * <pre>{@code
 * integrate-boot:
 *   resource-server:
 *     jwt:
 *       jwk-set-uri: http://auth-service/oauth2/jwks
 *       issuer: https://auth-service   # optional extra iss validation
 * }</pre>
 *
 * An application-defined {@link TokenValidationPort} bean always wins; with neither present,
 * the resource-server filter chain is not installed at all.
 */
@AutoConfiguration
@ConditionalOnClass(JwtDecoder.class)
@EnableConfigurationProperties(ResourceServerProperties.class)
public class JwtTokenValidationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TokenValidationPort.class)
    @ConditionalOnProperty("integrate-boot.resource-server.jwt.jwk-set-uri")
    public TokenValidationPort jwtTokenValidationPort(ResourceServerProperties properties) {
        String jwkSetUri = properties.getJwt().getJwkSetUri();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        if (StringUtils.hasText(properties.getJwt().getIssuer())) {
            decoder.setJwtValidator(
                    JwtValidators.createDefaultWithIssuer(properties.getJwt().getIssuer()));
        }
        return new JwtTokenValidationPort(decoder);
    }
}
