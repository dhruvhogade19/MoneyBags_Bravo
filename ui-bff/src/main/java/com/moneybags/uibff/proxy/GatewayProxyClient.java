package com.moneybags.uibff.proxy;

import com.moneybags.uibff.http.UpstreamExchange;
import com.moneybags.uibff.http.UpstreamResponse;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GatewayProxyClient {
    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            HttpHeaders.ACCEPT.toLowerCase(),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(),
            HttpHeaders.CONTENT_TYPE.toLowerCase(),
            HttpHeaders.IF_MATCH.toLowerCase(),
            HttpHeaders.IF_NONE_MATCH.toLowerCase(),
            HttpHeaders.RANGE.toLowerCase());

    private final RestClient gateway;

    public GatewayProxyClient(@Qualifier("gatewayRestClient") RestClient gateway) {
        this.gateway = gateway;
    }

    public UpstreamResponse authenticated(HttpMethod method, String path, String rawQuery,
                                          HttpHeaders browserHeaders, byte[] body,
                                          String accessToken, String tenantId,
                                          String correlationId, String idempotencyKey,
                                          String actorId) {
        HttpHeaders headers = copySafeHeaders(browserHeaders);
        headers.setBearerAuth(accessToken);
        headers.set("X-Tenant-ID", tenantId);
        headers.set("X-Correlation-ID", correlationId);
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        if (actorId != null && !actorId.isBlank()) headers.set("X-Actor-Id", actorId);
        return UpstreamExchange.exchange(gateway, method, uri(path, rawQuery), headers, body);
    }

    public UpstreamResponse publicGet(String path, String rawQuery, HttpHeaders browserHeaders) {
        return UpstreamExchange.exchange(gateway, HttpMethod.GET, uri(path, rawQuery),
                copySafeHeaders(browserHeaders), null);
    }

    private static HttpHeaders copySafeHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();
        source.forEach((name, values) -> {
            if (FORWARDED_REQUEST_HEADERS.contains(name.toLowerCase())) target.put(name, List.copyOf(values));
        });
        return target;
    }

    private static String uri(String path, String rawQuery) {
        return rawQuery == null || rawQuery.isBlank() ? path : path + "?" + rawQuery;
    }
}
