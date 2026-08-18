package com.moneybags.identity.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Starts an authorization request with a clean Identity session when the web
 * client explicitly asks to switch workspace identities. Browser cookies are
 * profile-wide, so this is required when a customer signs in after an admin
 * (or the other way around) in another tab.
 */
final class IdentitySwitchLoginFilter extends OncePerRequestFilter {
    static final String RESUME_AUTHORIZATION_ATTRIBUTE = "MONEYBAGS_RESUME_AUTHORIZATION";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"/oauth2/authorize".equals(request.getRequestURI()) || !"1".equals(request.getParameter("mb_switch"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String query = request.getParameterMap().entrySet().stream()
                .filter(entry -> !"mb_switch".equals(entry.getKey()))
                .flatMap(entry -> java.util.Arrays.stream(entry.getValue())
                        .map(value -> encode(entry.getKey()) + "=" + encode(value)))
                .collect(Collectors.joining("&"));
        String resume = request.getRequestURI() + (query.isBlank() ? "" : "?" + query);

        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        request.getSession(true).setAttribute(RESUME_AUTHORIZATION_ATTRIBUTE, resume);
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
