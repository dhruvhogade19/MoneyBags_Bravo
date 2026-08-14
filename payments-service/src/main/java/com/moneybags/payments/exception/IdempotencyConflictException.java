package com.moneybags.payments.exception;

public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String message) {
    super(message);
  }
}
