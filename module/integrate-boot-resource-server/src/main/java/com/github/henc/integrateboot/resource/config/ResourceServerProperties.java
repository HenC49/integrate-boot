package com.github.henc.integrateboot.resource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "integrate-boot.resource-server")
public class ResourceServerProperties {

    private List<String> permitAllPaths = new ArrayList<>();

    /** JWT validation settings for the default {@code JwtTokenValidationPort}. */
    private Jwt jwt = new Jwt();

    public List<String> getPermitAllPaths() {
        return permitAllPaths;
    }

    public void setPermitAllPaths(List<String> permitAllPaths) {
        this.permitAllPaths = permitAllPaths;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    /** JWKS endpoint + optional issuer check for the default JWT token validation. */
    public static class Jwt {

        /**
         * URL of the authorization server's JWKS endpoint. Setting this installs the default
         * JWT-backed {@code TokenValidationPort} unless the application defines its own.
         */
        private String jwkSetUri;

        /** Expected {@code iss} claim; when set, tokens from other issuers are rejected. */
        private String issuer;

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }
}
