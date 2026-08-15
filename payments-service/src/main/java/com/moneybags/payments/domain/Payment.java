package com.moneybags.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PAYMENT", uniqueConstraints = {
    @UniqueConstraint(name = "UK_PAYMENT_CIF_IDEMPOTENCY",
        columnNames = {"REQUESTOR_CIF_ID", "IDEMPOTENCY_KEY"})})
@Getter
@Setter
@NoArgsConstructor
public class Payment {
  @Id
  @Column(name = "PAYMENT_ID", length = 36, nullable = false, updatable = false)
  private String paymentId;

  @Column(name = "REQUESTOR_CIF_ID", nullable = false, updatable = false)
  private Long requestorCifId;

  @Column(name = "IDEMPOTENCY_KEY", length = 120, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "REQUEST_FINGERPRINT", length = 64, nullable = false, updatable = false)
  private String requestFingerprint;

  @Enumerated(EnumType.STRING)
  @Column(name = "PAYMENT_TYPE", length = 40, nullable = false, updatable = false)
  private PaymentType paymentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "SOURCE_INSTRUMENT_TYPE", length = 30, nullable = false, updatable = false)
  private InstrumentType sourceInstrumentType;

  @Column(name = "SOURCE_ACCOUNT_ID", length = 150, nullable = false, updatable = false)
  private String sourceAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "DESTINATION_INSTRUMENT_TYPE", length = 30, nullable = false, updatable = false)
  private InstrumentType destinationInstrumentType;

  @Column(name = "DESTINATION_ACCOUNT_ID", length = 150)
  private String destinationAccountId;

  @Column(name = "MERCHANT_ID", length = 150, updatable = false)
  private String merchantId;

  @Column(name = "BILL_ID", length = 100, updatable = false)
  private String billId;

  @Column(name = "FIXED_DEPOSIT_ID", length = 100, updatable = false)
  private String fixedDepositId;

  @Column(name = "PRINCIPAL_AMOUNT", precision = 19, scale = 4, updatable = false)
  private BigDecimal principalAmount;

  @Column(name = "INTEREST_AMOUNT", precision = 19, scale = 4, updatable = false)
  private BigDecimal interestAmount;

  @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false, updatable = false)
  private BigDecimal amount;

  @Column(name = "CURRENCY_CODE", length = 3, nullable = false, updatable = false)
  private String currencyCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "STATUS", length = 30, nullable = false)
  private PaymentStatus status;

  @Column(name = "REFERENCE_TEXT", length = 255)
  private String reference;

  @Column(name = "DEPOSIT_RESERVATION_ID", length = 100)
  private String depositReservationId;

  @Column(name = "CARD_HOLD_ID", length = 100)
  private String cardHoldId;

  @Column(name = "ACCOUNTING_JOURNAL_NUMBER", length = 100)
  private String accountingJournalNumber;

  @Column(name = "REVERSAL_JOURNAL_NUMBER", length = 100)
  private String reversalJournalNumber;

  @Column(name = "BILLING_SETTLEMENT_AT")
  private Instant billingSettlementAt;

  @Column(name = "FAILURE_CODE", length = 80)
  private String failureCode;

  @Column(name = "FAILURE_MESSAGE", length = 500)
  private String failureMessage;

  @Column(name = "CORRELATION_ID", length = 100, nullable = false, updatable = false)
  private String correlationId;

  @Column(name = "BUSINESS_DATE", nullable = false, updatable = false)
  private LocalDate businessDate;

  @Column(name = "CREATED_AT", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "UPDATED_AT", nullable = false)
  private Instant updatedAt;

  @Column(name = "SETTLED_AT")
  private Instant settledAt;

  @Column(name = "REVERSED_AT")
  private Instant reversedAt;

  @Version
  @Column(name = "VERSION_NO", nullable = false)
  private long version;

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = createdAt == null ? now : createdAt;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
