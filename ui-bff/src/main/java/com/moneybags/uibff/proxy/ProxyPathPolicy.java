package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriUtils;

public final class ProxyPathPolicy {
    private static final String AUTHENTICATED_PREFIX = "/api/proxy";
    private static final String PUBLIC_PRODUCT_PREFIX = "/api/public/products";

    private ProxyPathPolicy() {
    }

    public static String authenticatedGatewayPath(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(AUTHENTICATED_PREFIX)) {
            throw badPath();
        }
        String path = requestUri.substring(AUTHENTICATED_PREFIX.length());
        validateApiPath(path);
        return path;
    }

    public static String publicProductGatewayPath(String requestUri) {
        if (requestUri == null || !requestUri.startsWith(PUBLIC_PRODUCT_PREFIX)) {
            throw badPath();
        }
        String suffix = requestUri.substring(PUBLIC_PRODUCT_PREFIX.length());
        if (suffix.isEmpty() || suffix.equals("/")) return "/api/products";
        if (!suffix.matches("/[A-Za-z0-9-]+")) throw badPath();
        String path = "/api/products" + suffix;
        validateApiPath(path);
        return path;
    }

    private static void validateApiPath(String rawPath) {
        if (rawPath.isBlank() || !(rawPath.equals("/api") || rawPath.startsWith("/api/"))) {
            throw badPath();
        }
        String decoded = rawPath;
        for (int pass = 0; pass < 3; pass++) {
            String next;
            try {
                next = UriUtils.decode(decoded, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                throw badPath();
            }
            validateSegments(next);
            if (next.equals(decoded)) break;
            decoded = next;
        }
    }

    private static void validateSegments(String path) {
        if (path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) throw badPath();
        boolean rejected = Arrays.stream(path.split("/", -1))
                .anyMatch(segment -> segment.equals(".") || segment.equals("..")
                        || segment.equalsIgnoreCase("internal"));
        if (rejected) throw badPath();
    }

    private static BffApiException badPath() {
        return new BffApiException(HttpStatus.BAD_REQUEST,
                "Only public /api paths may be proxied; internal and traversal paths are blocked");
    }
}
