package com.lexisnexis.xmltojsoncontenttransformation.config;

import com.lexisnexis.xmltojsoncontenttransformation.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Guards the configured protected path prefix with a shared API key when one
 * is configured. Health probes, metrics and the Swagger docs stay public;
 * when no key is set (local development) the filter is a no-op.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        AppProperties.Security security = properties.getSecurity();
        String configuredKey = security.getApiKey();
        boolean disabled = configuredKey == null || configuredKey.isBlank();
        return disabled || !request.getRequestURI().startsWith(security.getProtectedPathPrefix());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = properties.getSecurity().getApiKeyHeader();
        String provided = request.getHeader(header);
        if (provided != null && constantTimeEquals(provided, properties.getSecurity().getApiKey())) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(
                HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                "Missing or invalid " + header + " header")));
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}