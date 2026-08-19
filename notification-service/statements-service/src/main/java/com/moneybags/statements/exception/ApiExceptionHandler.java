package com.moneybags.statements.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
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
}
