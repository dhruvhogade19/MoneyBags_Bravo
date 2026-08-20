package com.moneybags.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Operator-controlled audit record for restoring an authoritative legacy FD funding posting.
 * The immutable source snapshot deliberately remains in Payments, the service that owns the
 * original funding command; no Accounting or Deposit table is queried directly.
 */
@Entity
@Table(name = "PAYMENT_FD_ACCOUNTING_RECOVERY", uniqueConstraints = {
    @UniqueConstraint(name = "UK_PAY_FD_REC_PAYMENT", columnNames = "PAYMENT_ID"),
    @UniqueConstraint(name = "UK_PAY_FD_REC_KEY", columnNames = "IDEMPOTENCY_KEY_HASH")})
@Getter
@Setter
@NoArgsConstructor
public class FixedDepositAccountingRecovery {
  @Id
  @Column(name = "RECOVERY_ID", length = 36, nullable = false, updatable = false)
  private String recoveryId;

  @Column(name = "PAYMENT_ID", length = 36, nullable = false, updatable = false)
  private String paymentId;

  @Column(name = "IDEMPOTENCY_KEY_HASH", length = 64, nullable = false, updatable = false)
  private String idempotencyKeyHash;

  @Column(name = "REQUEST_HASH", length = 64, nullable = false, updatable = false)
  private String requestHash;

  @Column(name = "SOURCE_FINGERPRINT", length = 64, nullable = false, updatable = false)
  private String sourceFingerprint;

  @Column(name = "FIXED_DEPOSIT_ID", length = 100, nullable = false, updatable = false)
  private String fixedDepositId;

  @Column(name = "SOURCE_ACCOUNT_ID", length = 150, nullable = false, updatable = false)
  private String sourceAccountId;

  @Column(name = "FD_ACCOUNT_ID", length = 150, nullable = false, updatable = false)
  private String fixedDepositAccountId;

  @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(name = "CURRENCY_CODE", length = 3, nullable = false, updatable = false)
  private String currencyCode;

  @Column(name = "BUSINESS_DATE", nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(name = "ORIGINAL_OCCURRED_AT", nullable = false, updatable = false)
  private Instant originalOccurredAt;

  @Column(name = "ACCOUNTING_REFERENCE", length = 150, nullable = false, updatable = false)
  private String accountingReference;

  @Column(name = "LEGACY_JOURNAL_NUMBER", length = 100, nullable = false, updatable = false)
  private String legacyJournalNumber;

  @Column(name = "RECOVERY_JOURNAL_NUMBER", length = 100)
  private String recoveryJournalNumber;

  @Column(name = "STATUS", length = 30, nullable = false)
  private String status;

  @Column(name = "OUTCOME", length = 30)
  private String outcome;

  @Column(name = "REASON", length = 500, nullable = false, updatable = false)
  private String reason;

  @Column(name = "REQUESTED_BY", length = 150, nullable = false, updatable = false)
  private String requestedBy;

  @Column(name = "CORRELATION_ID", length = 100, nullable = false)
  private String correlationId;

  @Column(name = "ATTEMPT_COUNT", nullable = false)
  private int attemptCount;

  @Column(name = "ERROR_CODE", length = 100)
  private String errorCode;

  @Column(name = "ERROR_MESSAGE", length = 500)
  private String errorMessage;

  @Column(name = "CREATED_AT", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "LAST_ATTEMPT_AT", nullable = false)
  private Instant lastAttemptAt;

  @Column(name = "COMPLETED_AT")
  private Instant completedAt;
}
