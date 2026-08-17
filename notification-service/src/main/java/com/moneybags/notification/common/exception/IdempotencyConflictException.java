package com.moneybags.notification.common.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key was already used with different request content: " + idempotencyKey);
    }
}
