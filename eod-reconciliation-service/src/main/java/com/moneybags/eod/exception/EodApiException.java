package com.moneybags.eod.exception;

import org.springframework.http.HttpStatus;

public class EodApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public EodApiException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
