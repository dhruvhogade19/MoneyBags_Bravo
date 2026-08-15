package com.moneybags.payments.domain;

public enum PaymentStatus {
  PENDING_VALIDATION,
  PENDING_RESERVATION,
  PENDING_ACCOUNTING,
  PENDING_SETTLEMENT,
  PENDING_BILLING,
  SETTLED,
  FAILED,
  CANCELLED,
  REVERSAL_PENDING,
  REVERSED;

  public boolean isFinal() {
    return this == SETTLED || this == FAILED || this == CANCELLED || this == REVERSED;
  }
}
