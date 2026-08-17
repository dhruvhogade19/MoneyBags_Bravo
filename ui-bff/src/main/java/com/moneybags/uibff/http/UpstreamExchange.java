package com.moneybags.uibff.http;

import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public final class UpstreamExchange {
    private static final Set<String> RESPONSE_HEADERS = Set.of(
            HttpHeaders.CONTENT_TYPE.toLowerCase(),
            HttpHeaders.CONTENT_DISPOSITION.toLowerCase(),
            HttpHeaders.CACHE_CONTROL.toLowerCase(),
            HttpHeaders.ETAG.toLowerCase(),
            HttpHeaders.LAST_MODIFIED.toLowerCase(),
            HttpHeaders.RETRY_AFTER.toLowerCase());

    private UpstreamExchange() {
    }

    public static UpstreamResponse exchange(RestClient client, HttpMethod method, String uri,
                                            HttpHeaders requestHeaders, byte[] body) {
        RestClient.RequestBodySpec request = client.method(method).uri(uri)
                .headers(headers -> headers.addAll(requestHeaders));
        if (body != null && body.length > 0) request.body(body);
        return request.exchange((outgoing, incoming) -> {
            HttpHeaders safeHeaders = new HttpHeaders();
            incoming.getHeaders().forEach((name, values) -> {
                if (RESPONSE_HEADERS.contains(name.toLowerCase())) safeHeaders.put(name, values);
            });
            try {
                return new UpstreamResponse(incoming.getStatusCode(), safeHeaders,
                        incoming.getBody().readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read the upstream response", exception);
            }
        });
    }

    public static HttpHeaders contentHeaders(String contentType) {
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null && !contentType.isBlank()) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        }
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.ALL));
        return headers;
    }
}
