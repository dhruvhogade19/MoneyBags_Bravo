package com.moneybags.payments.api;

import com.moneybags.payments.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  public record ApiError(Instant timestamp, int status, String code, String message,
                         String correlationId, List<String> details) { }

  @ExceptionHandler(ResourceNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  ApiError notFound(ResourceNotFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request,
        List.of());
  }

  @ExceptionHandler(BusinessValidationException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  ApiError business(BusinessValidationException exception, HttpServletRequest request) {
    return error(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_VALIDATION_FAILED",
        "Business validation failed", request, exception.getErrors());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiError idempotency(IdempotencyConflictException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage(), request,
        List.of());
  }

  @ExceptionHandler(PaymentCutoffException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiError cutoff(PaymentCutoffException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "PAYMENT_CUTOFF_ACTIVE", exception.getMessage(), request,
        List.of());
  }

  @ExceptionHandler(PeerServiceException.class)
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  ApiError peer(PeerServiceException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_GATEWAY, exception.getCode(), exception.getMessage(), request,
        List.of("service=" + exception.getService(), "peerStatus=" + exception.getStatus()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
        request, exception.getBindingResult().getFieldErrors().stream()
            .map(value -> value.getField() + ": " + value.getDefaultMessage()).toList());
  }

  @ExceptionHandler(BindException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError invalidBinding(BindException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
        request, exception.getBindingResult().getFieldErrors().stream()
            .map(value -> value.getField() + ": " + value.getDefaultMessage()).toList());
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  ApiError invalidConstraint(ConstraintViolationException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
        request, exception.getConstraintViolations().stream()
            .map(value -> value.getPropertyPath() + ": " + value.getMessage()).toList());
  }

  @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
  @ResponseStatus(HttpStatus.CONFLICT)
  ApiError conflict(Exception exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
        "Concurrent update or duplicate data", request, List.of());
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  ApiError unknown(Exception exception, HttpServletRequest request) {
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
        "An unexpected error occurred", request, List.of());
  }

  private ApiError error(HttpStatus status, String code, String message,
                         HttpServletRequest request, List<String> details) {
    String correlationId = Optional.ofNullable(MDC.get("correlationId"))
        .orElseGet(() -> Optional.ofNullable(request.getHeader("X-Correlation-Id")).orElse(""));
    return new ApiError(Instant.now(), status.value(), code, message, correlationId, details);
  }
}
