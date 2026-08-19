package com.moneybags.statements.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(StatementException.class)
    ResponseEntity<Map<String, Object>> statement(StatementException e, HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("status", e.getStatus().value(), "code", e.getCode(), "detail", e.getMessage(), "instance", request.getRequestURI()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalid(IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "code", "VALIDATION_FAILED", "detail", e.getMessage(), "instance", request.getRequestURI()));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, Object>> upstream(RestClientException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", 503,
                "code", "STATEMENT_SOURCE_UNAVAILABLE",
                "detail", "A required statement data source is unavailable",
                "instance", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, Object>> invalidUpstreamResponse(IllegalStateException e,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "status", 502,
                "code", "INVALID_STATEMENT_SOURCE_RESPONSE",
                "detail", e.getMessage(),
                "instance", request.getRequestURI()));
    }
}
