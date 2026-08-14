package com.moneybags.payments.service;

import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentAttempt;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentStatusHistory;
import com.moneybags.payments.dto.PaymentDtos.PaymentResponse;
import com.moneybags.payments.exception.IdempotencyConflictException;
import com.moneybags.payments.repository.PaymentAttemptRepository;
import com.moneybags.payments.repository.PaymentRepository;
import com.moneybags.payments.repository.PaymentStatusHistoryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class PaymentSupportService {
  private final PaymentRepository payments;
  private final PaymentStatusHistoryRepository history;
  private final PaymentAttemptRepository attempts;

  public PaymentSupportService(PaymentRepository payments,
                               PaymentStatusHistoryRepository history,
                               PaymentAttemptRepository attempts) {
    this.payments = payments;
    this.history = history;
    this.attempts = attempts;
  }

  public Optional<PaymentResponse> existing(Long cifId, String key, String fingerprint) {
    return payments.findByRequestorCifIdAndIdempotencyKey(cifId, key).map(payment -> {
      if (!payment.getRequestFingerprint().equals(fingerprint)) {
        throw new IdempotencyConflictException(
            "Idempotency-Key was already used with different request data");
      }
      return response(payment);
    });
  }

  public void initial(Payment payment) {
    payments.save(payment);
    recordTransition(payment, null, PaymentStatus.PENDING_VALIDATION, "PAYMENT_CREATED", null);
  }

  public void transition(Payment payment, PaymentStatus next, String reasonCode,
                         String reasonMessage) {
    PaymentStatus previous = payment.getStatus();
    payment.setStatus(next);
    if (next == PaymentStatus.SETTLED) {
      payment.setSettledAt(Instant.now());
    } else if (next == PaymentStatus.REVERSED) {
      payment.setReversedAt(Instant.now());
    }
    payments.save(payment);
    recordTransition(payment, previous, next, reasonCode, reasonMessage);
  }

  public <T> T attempt(Payment payment, String step, String target, Supplier<T> operation) {
    Instant started = Instant.now();
    try {
      T result = operation.get();
      recordAttempt(payment, step, target, "SUCCESS", null, null, started);
      return result;
    } catch (RuntimeException exception) {
      recordAttempt(payment, step, target, "FAILED", null,
          exception.getClass().getSimpleName(), started);
      throw exception;
    }
  }

  public void recordIgnoredFailure(Payment payment, String step, String target,
                                   RuntimeException exception) {
    recordAttempt(payment, step, target, "FAILED_IGNORED", null,
        exception.getClass().getSimpleName(), Instant.now());
  }

  public static String fingerprint(Object... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (Object value : values) {
        digest.update((String.valueOf(value) + "|").getBytes(StandardCharsets.UTF_8));
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static String newPaymentId() {
    return "PAY-" + UUID.randomUUID().toString().replace("-", "");
  }

  public static PaymentResponse response(Payment p) {
    return new PaymentResponse(p.getPaymentId(), p.getRequestorCifId(), p.getPaymentType(),
        p.getSourceInstrumentType(), p.getSourceAccountId(), p.getDestinationInstrumentType(),
        p.getDestinationAccountId(), p.getMerchantId(), p.getBillId(), p.getFixedDepositId(),
        p.getPrincipalAmount(), p.getInterestAmount(), p.getAmount(), p.getCurrencyCode(),
        p.getStatus(), p.getReference(), p.getDepositReservationId(),
        p.getCardHoldId(), p.getAccountingJournalNumber(), p.getReversalJournalNumber(),
        p.getFailureCode(), p.getFailureMessage(), p.getCorrelationId(), p.getBusinessDate(),
        p.getCreatedAt(), p.getUpdatedAt(), p.getSettledAt(), p.getReversedAt());
  }

  private void recordTransition(Payment payment, PaymentStatus from, PaymentStatus to,
                                String code, String message) {
    PaymentStatusHistory row = new PaymentStatusHistory();
    row.setPaymentId(payment.getPaymentId());
    row.setFromStatus(from);
    row.setToStatus(to);
    row.setReasonCode(code);
    row.setReasonMessage(message);
    row.setCorrelationId(payment.getCorrelationId());
    row.setChangedAt(Instant.now());
    history.save(row);
  }

  private void recordAttempt(Payment payment, String step, String target, String outcome,
                             Integer httpStatus, String errorCode, Instant started) {
    PaymentAttempt row = new PaymentAttempt();
    row.setPaymentId(payment.getPaymentId());
    row.setStepCode(step);
    row.setTargetService(target);
    row.setOutcome(outcome);
    row.setHttpStatus(httpStatus);
    row.setErrorCode(errorCode);
    row.setStartedAt(started);
    row.setCompletedAt(Instant.now());
    attempts.save(row);
  }
}
