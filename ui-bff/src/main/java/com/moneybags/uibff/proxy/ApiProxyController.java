package com.moneybags.uibff.proxy;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiProxyController {
    private final AuthorizedSessionResolver sessions;
    private final CustomerKycAccessGuard accessGuard;
    private final GatewayProxyClient gateway;

    public ApiProxyController(AuthorizedSessionResolver sessions,
                              CustomerKycAccessGuard accessGuard,
                              GatewayProxyClient gateway) {
        this.sessions = sessions;
        this.accessGuard = accessGuard;
        this.gateway = gateway;
    }

    @RequestMapping("/api/proxy/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request, HttpServletResponse servletResponse,
                                        Authentication authentication) throws IOException {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        String path = ProxyPathPolicy.authenticatedGatewayPath(request.getRequestURI());
        String idempotencyKey = IdempotencyKeys.preserveOrGenerate(
                method, request.getHeader("Idempotency-Key"));
        String correlationId = UUID.randomUUID().toString();
        var session = sessions.resolve(authentication, request, servletResponse);
        accessGuard.authorize(session, method, path, correlationId);
        var response = gateway.authenticated(
                method,
                path,
                request.getQueryString(),
                headers(request),
                request.getInputStream().readAllBytes(),
                session.accessToken(),
                session.tenantId(),
                correlationId,
                idempotencyKey,
                session.actorId());

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.addAll(response.headers());
        responseHeaders.set("X-Correlation-ID", correlationId);
        if (idempotencyKey != null) responseHeaders.set("Idempotency-Key", idempotencyKey);
        return ResponseEntity.status(response.status()).headers(responseHeaders).body(response.body());
    }

    private static HttpHeaders headers(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name ->
                request.getHeaders(name).asIterator().forEachRemaining(value -> headers.add(name, value)));
        return headers;
    }
}
