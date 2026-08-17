package com.moneybags.uibff.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BffExceptionHandler {
    @ExceptionHandler(BffApiException.class)
    ResponseEntity<ProblemDetail> handle(BffApiException exception, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setTitle(exception.status().getReasonPhrase());
        problem.setProperty("path", request.getRequestURI());
        return ResponseEntity.status(exception.status()).body(problem);
    }
}
