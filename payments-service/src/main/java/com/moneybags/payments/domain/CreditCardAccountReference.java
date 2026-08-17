package com.moneybags.payments.domain;

import java.util.Locale;

/**
 * Keeps the cross-service credit-card subledger reference stable while the
 * Credit Card service still exposes numeric database identifiers in its URLs.
 */
public final class CreditCardAccountReference {
  private static final String PREFIX = "CC-";

  private CreditCardAccountReference() { }

  public static String canonical(String accountId) {
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalArgumentException("Credit-card account reference is required");
    }
    String trimmed = accountId.trim();
    if (trimmed.toUpperCase(Locale.ROOT).startsWith(PREFIX)) {
      return PREFIX + trimmed.substring(PREFIX.length());
    }
    return PREFIX + trimmed;
  }

  public static String serviceAccountId(String accountReference) {
    String canonical = canonical(accountReference);
    return canonical.substring(PREFIX.length());
  }
}
