package com.moneybags.deposit.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record FieldError(String field, String message) {}
    public record Problem(String code, String message, int status, String path, String correlationId,
                          OffsetDateTime timestamp, List<FieldError> errors) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Problem> api(ApiException ex, HttpServletRequest request) {
        return response(ex.status(), ex.code(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, errors);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Problem> optimistic(HttpServletRequest request) {
        return response(HttpStatus.PRECONDITION_FAILED, "STALE_ACCOUNT_VERSION",
                "The account changed; refresh and retry with the latest version", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled API error for {} (correlationId={})", request.getRequestURI(),
                MDC.get("correlationId"), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", request, List.of());
    }

    private ResponseEntity<Problem> response(HttpStatus status, String code, String message,
                                             HttpServletRequest request, List<FieldError> errors) {
        return ResponseEntity.status(status).body(new Problem(code, message, status.value(), request.getRequestURI(),
                MDC.get("correlationId"), OffsetDateTime.now(), errors));
    }
}
