package com.moneybags.accounting.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record FieldError(String field, String message) {}
    public record Problem(String type, String title, int status, String detail, String code, String instance,
                          String correlationId, OffsetDateTime timestamp, List<FieldError> errors) {}

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Problem> api(ApiException ex, HttpServletRequest request) {
        return response(ex.status(), ex.code(), ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problem> forbidden(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "The authenticated identity is not allowed to access this resource", request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problem> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, errors);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<Problem> malformed(Exception ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request content or parameter format is invalid",
                request, List.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Problem> header(MissingRequestHeaderException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "REQUIRED_HEADER_MISSING",
                "Required header is missing: " + ex.getHeaderName(), request, List.of());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Problem> optimistic(HttpServletRequest request) {
        return response(HttpStatus.PRECONDITION_FAILED, "STALE_RESOURCE_VERSION",
                "The resource changed; refresh and retry with the latest version", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problem> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled Accounting API error for {} (correlationId={})", request.getRequestURI(),
                MDC.get("correlationId"), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred",
                request, List.of());
    }

    private ResponseEntity<Problem> response(HttpStatus status, String code, String detail,
                                             HttpServletRequest request, List<FieldError> errors) {
        Problem problem = new Problem("urn:moneybags:accounting:error:" + code.toLowerCase(),
                status.getReasonPhrase(), status.value(), detail, code, request.getRequestURI(),
                MDC.get("correlationId"), OffsetDateTime.now(), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }
}
