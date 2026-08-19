package com.moneybags.statements.exception;

import org.springframework.http.HttpStatus;
public class StatementException extends RuntimeException {
    private final HttpStatus status; private final String code;
    public StatementException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus getStatus() { return status; } public String getCode() { return code; }
}
