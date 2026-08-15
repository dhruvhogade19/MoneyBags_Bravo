package com.moneybags.accounting.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String value = Optional.ofNullable(request.getHeader(HEADER))
                .filter(id -> id.matches("[A-Za-z0-9._:-]{1,64}"))
                .orElseGet(() -> UUID.randomUUID().toString());
        MDC.put("correlationId", value); response.setHeader(HEADER, value);
        try { filterChain.doFilter(request, response); } finally { MDC.remove("correlationId"); }
    }
}
