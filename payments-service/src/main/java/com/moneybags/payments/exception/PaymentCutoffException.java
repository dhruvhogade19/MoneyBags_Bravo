package com.moneybags.payments.exception;

public class PaymentCutoffException extends RuntimeException {
  public PaymentCutoffException(String message) {
    super(message);
  }
}
