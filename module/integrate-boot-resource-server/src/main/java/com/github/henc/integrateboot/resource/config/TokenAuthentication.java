package com.github.henc.integrateboot.resource.config;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.util.ArrayList;

public class TokenAuthentication extends AbstractAuthenticationToken {

    private final String token;
    private final String subject;

    public TokenAuthentication(String token, String subject, Iterable<String> authorities) {
        super(new ArrayList<>(toList(authorities)));
        this.token = token;
        this.subject = subject;
        setAuthenticated(true);
    }

    private static java.util.List<org.springframework.security.core.GrantedAuthority> toList(
            Iterable<String> authorities) {
        java.util.List<org.springframework.security.core.GrantedAuthority> result = new ArrayList<>();
        for (String authority : authorities) {
            result.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(authority));
        }
        return result;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return subject;
    }
}
