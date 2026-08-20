package com.moneybags.payments.service;

import com.moneybags.payments.domain.FixedDepositAccountingRecovery;
import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryCandidate;
import com.moneybags.payments.exception.IdempotencyConflictException;
import com.moneybags.payments.exception.ResourceNotFoundException;
import com.moneybags.payments.repository.FixedDepositAccountingRecoveryRepository;
import com.moneybags.payments.repository.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FixedDepositAccountingRecoveryStore {
  private final FixedDepositAccountingRecoveryRepository recoveries;
  private final PaymentRepository payments;

  public FixedDepositAccountingRecoveryStore(
      FixedDepositAccountingRecoveryRepository recoveries, PaymentRepository payments) {
    this.recoveries = recoveries;
    this.payments = payments;
  }

  @Transactional
  public FixedDepositAccountingRecovery begin(
      FixedDepositAccountingRecoveryCandidate candidate,
      String idempotencyKeyHash,
      String requestHash,
      String reason,
      String requestedBy,
      String correlationId) {
    FixedDepositAccountingRecovery recovery = recoveries.findByPaymentId(candidate.paymentId())
        .orElse(null);
    if (recovery != null) {
      assertSameCommand(recovery, idempotencyKeyHash, requestHash);
      recovery.setStatus("PENDING");
      recovery.setOutcome(null);
      recovery.setRecoveryJournalNumber(null);
      recovery.setErrorCode(null);
      recovery.setErrorMessage(null);
      recovery.setCompletedAt(null);
      recovery.setCorrelationId(correlationId);
      recovery.setLastAttemptAt(Instant.now());
      recovery.setAttemptCount(recovery.getAttemptCount() + 1);
      return recoveries.save(recovery);
    }
    recoveries.findByIdempotencyKeyHash(idempotencyKeyHash).ifPresent(duplicate -> {
      throw new IdempotencyConflictException(
          "Idempotency-Key was already used for another FD accounting recovery");
    });
    Instant now = Instant.now();
    recovery = new FixedDepositAccountingRecovery();
    recovery.setRecoveryId(UUID.randomUUID().toString());
    recovery.setPaymentId(candidate.paymentId());
    recovery.setIdempotencyKeyHash(idempotencyKeyHash);
    recovery.setRequestHash(requestHash);
    recovery.setSourceFingerprint(candidate.sourceFingerprint());
    recovery.setFixedDepositId(candidate.fixedDepositId());
    recovery.setSourceAccountId(candidate.sourceAccountId());
    recovery.setFixedDepositAccountId(candidate.fixedDepositAccountId());
    recovery.setAmount(candidate.amount());
    recovery.setCurrencyCode(candidate.currencyCode());
    recovery.setBusinessDate(candidate.businessDate());
    recovery.setOriginalOccurredAt(candidate.originalOccurredAt());
    recovery.setAccountingReference(candidate.accountingReference());
    recovery.setLegacyJournalNumber(candidate.legacyJournalNumber());
    recovery.setStatus("PENDING");
    recovery.setReason(reason);
    recovery.setRequestedBy(requestedBy);
    recovery.setCorrelationId(correlationId);
    recovery.setAttemptCount(1);
    recovery.setCreatedAt(now);
    recovery.setLastAttemptAt(now);
    return recoveries.save(recovery);
  }

  @Transactional
  public FixedDepositAccountingRecovery complete(
      String recoveryId, String journalNumber, String outcome, String correlationId) {
    FixedDepositAccountingRecovery recovery = recoveries.findByIdForUpdate(recoveryId)
        .orElseThrow(() -> new ResourceNotFoundException("FD accounting recovery", recoveryId));
    Payment payment = payments.findByIdForUpdate(recovery.getPaymentId())
        .orElseThrow(() -> new ResourceNotFoundException("Payment", recovery.getPaymentId()));
    payment.setAccountingJournalNumber(journalNumber);
    payments.save(payment);
    recovery.setRecoveryJournalNumber(journalNumber);
    recovery.setStatus("COMPLETED");
    recovery.setOutcome(outcome);
    recovery.setCorrelationId(correlationId);
    recovery.setErrorCode(null);
    recovery.setErrorMessage(null);
    recovery.setCompletedAt(Instant.now());
    return recoveries.save(recovery);
  }

  @Transactional
  public void fail(String recoveryId, String errorCode, String errorMessage,
                   String correlationId) {
    recoveries.findByIdForUpdate(recoveryId).ifPresent(recovery -> {
      recovery.setStatus("FAILED");
      recovery.setOutcome(null);
      recovery.setErrorCode(limit(errorCode, 100));
      recovery.setErrorMessage(limit(errorMessage, 500));
      recovery.setCorrelationId(correlationId);
      recovery.setCompletedAt(Instant.now());
      recoveries.save(recovery);
    });
  }

  static void assertSameCommand(FixedDepositAccountingRecovery recovery,
                                String idempotencyKeyHash, String requestHash) {
    if (!recovery.getIdempotencyKeyHash().equals(idempotencyKeyHash)
        || !recovery.getRequestHash().equals(requestHash)) {
      throw new IdempotencyConflictException(
          "This payment already has an FD accounting recovery with different command data");
    }
  }

  private static String limit(String value, int length) {
    if (value == null) return null;
    return value.length() <= length ? value : value.substring(0, length);
  }
}
