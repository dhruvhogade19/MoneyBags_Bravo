package com.moneybags.payments.exception;

public class ResourceNotFoundException extends RuntimeException {
  public ResourceNotFoundException(String type, String id) {
    super(type + " not found: " + id);
  }
}
