package com.moneybags.deposit.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Converts Accounting protocol failures into stable Deposit integration errors. */
final class AccountingUpstreamErrors {
    private static final ObjectMapper JSON = new ObjectMapper();

    private AccountingUpstreamErrors() { }

    static ApiException response(String operation, RestClientResponseException failure) {
        UpstreamProblem problem = parse(failure);
        int upstreamStatus = failure.getStatusCode().value();
        HttpStatus status = upstreamStatus >= 400 && upstreamStatus < 500
                ? HttpStatus.resolve(upstreamStatus) : HttpStatus.BAD_GATEWAY;
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        String code = switch (upstreamStatus) {
            case 400, 422 -> "ACCOUNTING_REQUEST_REJECTED";
            case 401 -> "ACCOUNTING_AUTHENTICATION_REJECTED";
            case 403 -> "ACCOUNTING_AUTHORIZATION_REJECTED";
            case 404 -> "ACCOUNTING_RESOURCE_NOT_FOUND";
            case 409 -> "ACCOUNTING_POSTING_REJECTED";
            case 429 -> "ACCOUNTING_RATE_LIMITED";
            default -> upstreamStatus >= 500
                    ? "ACCOUNTING_UPSTREAM_FAILURE" : "ACCOUNTING_UPSTREAM_REJECTED";
        };
        return new ApiException(status, code, operation + " failed: upstreamCode=" + problem.code()
                + ", upstreamCorrelationId=" + problem.correlationId() + ", detail=" + problem.detail());
    }

    static ApiException unavailable(String operation, RestClientException failure) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNTING_UNAVAILABLE",
                operation + " could not reach Accounting: " + safe(failure.getMessage(), "connection failed"));
    }

    private static UpstreamProblem parse(RestClientResponseException failure) {
        String fallbackCode = "HTTP_" + failure.getStatusCode().value();
        try {
            JsonNode body = JSON.readTree(failure.getResponseBodyAsString());
            return new UpstreamProblem(text(body, "code", fallbackCode),
                    text(body, "detail", text(body, "message", "Accounting rejected the request")),
                    text(body, "correlationId", "unavailable"));
        } catch (Exception ignored) {
            return new UpstreamProblem(fallbackCode, "Accounting rejected the request", "unavailable");
        }
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null) return fallback;
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? safe(value.asText(), fallback) : fallback;
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String singleLine = value.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 500 ? singleLine : singleLine.substring(0, 500);
    }

    private record UpstreamProblem(String code, String detail, String correlationId) { }
}
