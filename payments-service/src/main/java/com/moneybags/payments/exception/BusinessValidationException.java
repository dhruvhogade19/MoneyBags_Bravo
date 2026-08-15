package com.moneybags.payments.exception;

import java.util.List;

public class BusinessValidationException extends RuntimeException {
  private final List<String> errors;

  public BusinessValidationException(String message) {
    this(List.of(message));
  }

  public BusinessValidationException(List<String> errors) {
    super("Business validation failed");
    this.errors = errors;
  }

  public List<String> getErrors() {
    return errors;
  }
}
