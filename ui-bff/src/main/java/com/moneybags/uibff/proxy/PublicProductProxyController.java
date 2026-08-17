package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import com.moneybags.uibff.http.UpstreamResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
public class PublicProductProxyController {
    private static final List<String> PUBLIC_LIST_PARAMETERS = List.of(
            "category", "subtype", "productName", "page", "size", "sort");

    private final GatewayProxyClient gateway;
    private final ObjectMapper objectMapper;

    public PublicProductProxyController(GatewayProxyClient gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    @GetMapping({"/api/public/products", "/api/public/products/"})
    public ResponseEntity<byte[]> products(HttpServletRequest request) {
        var response = sanitizeActiveList(gateway.publicGet(
                "/api/products", activeListQuery(request), headers(request)));
        return ResponseEntity.status(response.status()).headers(response.headers()).body(response.body());
    }

    @GetMapping("/api/public/products/{productCode}")
    public ResponseEntity<byte[]> product(@PathVariable String productCode, HttpServletRequest request) {
        String path = ProxyPathPolicy.publicProductGatewayPath(request.getRequestURI());
        var response = gateway.publicGet(path, null, headers(request));
        requireActiveDetail(response);
        return ResponseEntity.status(response.status()).headers(response.headers()).body(response.body());
    }

    private String activeListQuery(HttpServletRequest request) {
        var parameters = new java.util.ArrayList<String>();
        PUBLIC_LIST_PARAMETERS.forEach(name -> {
            String[] values = request.getParameterValues(name);
            if (values == null) return;
            for (String value : values) {
                parameters.add(UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8)
                        + "=" + UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8));
            }
        });
        // Ignore a caller-supplied status completely; anonymous catalogue reads are always ACTIVE-only.
        parameters.add("status=ACTIVE");
        return String.join("&", parameters);
    }

    private UpstreamResponse sanitizeActiveList(UpstreamResponse response) {
        if (!response.status().is2xxSuccessful()) return response;
        JsonNode root = readJson(response.body());
        int removed;
        if (root.isArray()) {
            ArrayNode products = (ArrayNode) root;
            ArrayNode active = activeOnly(products);
            removed = products.size() - active.size();
            root = active;
        } else if (root.isObject() && root.path("content").isArray()) {
            ObjectNode page = (ObjectNode) root;
            ArrayNode products = (ArrayNode) page.path("content");
            ArrayNode active = activeOnly(products);
            removed = products.size() - active.size();
            page.set("content", active);
            if (removed > 0 && page.path("totalElements").canConvertToLong()) {
                page.put("totalElements", Math.max(0, page.path("totalElements").asLong() - removed));
            }
            root = page;
        } else {
            throw invalidCatalogueResponse();
        }
        if (removed == 0) return response;
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.headers());
        headers.remove(HttpHeaders.ETAG);
        return new UpstreamResponse(response.status(), headers, writeJson(root));
    }

    private ArrayNode activeOnly(ArrayNode products) {
        ArrayNode active = objectMapper.createArrayNode();
        products.forEach(product -> {
            if (isActive(product)) active.add(product);
        });
        return active;
    }

    private void requireActiveDetail(UpstreamResponse response) {
        if (!response.status().is2xxSuccessful()) return;
        JsonNode product = readJson(response.body());
        if (!product.isObject()) throw invalidCatalogueResponse();
        if (!isActive(product)) {
            throw new BffApiException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    private static boolean isActive(JsonNode product) {
        return "ACTIVE".equals(product.path("status").asText());
    }

    private JsonNode readJson(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (JacksonException exception) {
            throw invalidCatalogueResponse();
        }
    }

    private byte[] writeJson(JsonNode body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JacksonException exception) {
            throw invalidCatalogueResponse();
        }
    }

    private static BffApiException invalidCatalogueResponse() {
        return new BffApiException(HttpStatus.BAD_GATEWAY,
                "The product catalogue returned an invalid response");
    }

    private static HttpHeaders headers(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name ->
                request.getHeaders(name).asIterator().forEachRemaining(value -> headers.add(name, value)));
        return headers;
    }
}
