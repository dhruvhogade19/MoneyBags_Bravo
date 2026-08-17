package com.moneybags.eod;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
class EodApiExceptionHandler {
    @ExceptionHandler(EodNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND) Map<String, Object> notFound(EodNotFoundException exception) { return error("NOT_FOUND", exception.getMessage()); }
    @ExceptionHandler(EodConflictException.class) @ResponseStatus(HttpStatus.CONFLICT) Map<String, Object> conflict(EodConflictException exception) { return error("CONFLICT", exception.getMessage()); }
    private Map<String, Object> error(String code, String message) { return Map.of("code", code, "message", message, "timestamp", Instant.now().toString()); }
}
class EodNotFoundException extends RuntimeException { EodNotFoundException(String message) { super(message); } }
class EodConflictException extends RuntimeException { EodConflictException(String message) { super(message); } }
