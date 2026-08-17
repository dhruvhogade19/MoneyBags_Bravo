package com.moneybags.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@ConditionalOnProperty(name = "moneybags.security.enabled", havingValue = "true", matchIfMissing = true)
public class TrustedIdentityHeadersFilter implements WebFilter, Ordered {
    private static final Set<String> UNTRUSTED_IDENTITY_HEADERS = Set.of(
            "X-User-ID", "X-Customer-ID", "X-Roles", "X-Authenticated-User");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/actuator/health")
                || isPublicProductGet(exchange.getRequest().getMethod(), path)) return chain.filter(exchange);
        return exchange.getPrincipal().flatMap(principal -> {
            if (!(principal instanceof JwtAuthenticationToken jwt)) return forbidden(exchange, "JWT authentication is required");
            String tenantHeader = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");
            String tenantClaim = jwt.getToken().getClaimAsString("tenant_id");
            if (tenantHeader == null || tenantHeader.isBlank() || !tenantHeader.equals(tenantClaim)) {
                return forbidden(exchange, "X-Tenant-ID must match the signed tenant_id claim");
            }
            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
            if (!validUuid(correlationId)) return badRequest(exchange, "X-Correlation-ID must be a UUID");

            var request = exchange.getRequest().mutate().headers(headers -> {
                UNTRUSTED_IDENTITY_HEADERS.forEach(headers::remove);
                headers.set("X-Authenticated-User", jwt.getName());
                String customerId = jwt.getToken().getClaimAsString("customer_id");
                if (customerId != null) headers.set("X-Customer-ID", customerId);
            }).build();
            return chain.filter(exchange.mutate().request(request).build());
        });
    }

    private boolean validUuid(String value) {
        if (value == null) return false;
        try { UUID.fromString(value); return true; } catch (IllegalArgumentException ignored) { return false; }
    }

    static boolean isPublicProductGet(HttpMethod method, String path) {
        if (method != HttpMethod.GET || path == null) return false;
        return path.equals("/api/products") || path.startsWith("/api/products/")
                || path.equals("/api/v1/products") || path.startsWith("/api/v1/products/");
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.FORBIDDEN, "Forbidden", detail);
    }

    private Mono<Void> badRequest(ServerWebExchange exchange, String detail) {
        return problem(exchange, HttpStatus.BAD_REQUEST, "Invalid request headers", detail);
    }

    private Mono<Void> problem(ServerWebExchange exchange, HttpStatus status, String title, String detail) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":"
                + status.value() + ",\"detail\":\"" + detail + "\",\"instance\":\""
                + exchange.getRequest().getPath().value() + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override public int getOrder() { return -50; }
}
