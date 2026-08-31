package com.github.henc.integrateboot.resource.config;

import com.github.henc.integrateboot.resource.TokenValidationPort;
import com.github.henc.integrateboot.resource.TokenValidationResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ResourceServerFilter extends OncePerRequestFilter {

    private final TokenValidationPort validationPort;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public ResourceServerFilter(TokenValidationPort validationPort, AuthenticationEntryPoint authenticationEntryPoint) {
        this.validationPort = validationPort;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(request, response);
            return;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            reject(request, response);
            return;
        }
        TokenValidationResult result;
        try {
            result = validationPort.validate(token);
        } catch (RuntimeException ex) {
            result = TokenValidationResult.invalid();
        }
        if (result == null || !result.valid()) {
            reject(request, response);
            return;
        }
        TokenAuthentication authentication = new TokenAuthentication(token, result.subject(), result.authorities());
        authentication.setDetails(result.claims());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid bearer token"));
    }
}
