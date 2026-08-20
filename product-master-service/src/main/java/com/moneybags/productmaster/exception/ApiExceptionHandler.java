package com.moneybags.productmaster.exception;

import com.moneybags.productmaster.exception.ProductExceptions.BusinessValidationException;
import com.moneybags.productmaster.exception.ProductExceptions.ProductNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(Instant timestamp, int status, String error, String message,
                           String path, List<String> validationMessages) {}

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiError> notFound(ProductNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessValidationException.class)
    ResponseEntity<ApiError> business(BusinessValidationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request,
                exception.getValidationMessages());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> messages = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return response(HttpStatus.BAD_REQUEST, "Request validation failed", request, messages);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Request body contains an unsupported or malformed value",
                request, List.of());
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message,
                                              HttpServletRequest request, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), details));
    }
}
