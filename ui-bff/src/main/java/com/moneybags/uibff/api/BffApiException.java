package com.moneybags.uibff.api;

import org.springframework.http.HttpStatus;

public class BffApiException extends RuntimeException {
    private final HttpStatus status;

    public BffApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
