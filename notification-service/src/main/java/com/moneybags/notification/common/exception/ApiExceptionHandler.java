package com.moneybags.notification.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError).toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidParameter(ConstraintViolationException exception) {
        return response(HttpStatus.BAD_REQUEST, "Validation failed", exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage()).toList());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Request body is invalid", List.of());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(IdempotencyConflictException exception) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), List.of());
    }

    @ExceptionHandler({CustomerNotFoundException.class, NotificationTemplateNotFoundException.class,
            NotificationNotFoundException.class})
    ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), List.of());
    }

    @ExceptionHandler({CifUnavailableException.class})
    ResponseEntity<ApiErrorResponse> handleCifUnavailable(CifUnavailableException exception) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), List.of());
    }

    @ExceptionHandler({InvalidCustomerEmailException.class, TemplateRenderingException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> handleUnprocessable(RuntimeException exception) {
        return response(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage(), List.of());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message, List<String> details) {
        ApiErrorResponse error = new ApiErrorResponse(
                OffsetDateTime.now(ZoneOffset.UTC), status.value(), status.getReasonPhrase(), message,
                MDC.get("correlationId"), details);
        return ResponseEntity.status(status).body(error);
    }
}
