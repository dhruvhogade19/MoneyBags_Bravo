package com.moneybags.payments.integration.demo;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.integration.CreditCardClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoCreditCardClient implements CreditCardClient {
  private final AtomicLong sequence = new AtomicLong(500);
  private final Map<String, CardHoldResponse> holdsByPayment = new ConcurrentHashMap<>();
  private final Map<String, BigDecimal> outstanding = new ConcurrentHashMap<>();

  @Override
  public CardHoldResponse createHold(String accountId, String paymentId, BigDecimal amount,
                                     String correlationId) {
    if (accountId.toLowerCase().contains("insufficient")
        || amount.compareTo(new BigDecimal("100000.00")) > 0) {
      throw new PeerServiceException("CREDIT-CARD-SERVICE", 409,
          "INSUFFICIENT_LIMIT", "Insufficient available credit limit");
    }
    return holdsByPayment.computeIfAbsent(paymentId, ignored -> new CardHoldResponse(
        sequence.incrementAndGet(), Long.valueOf(accountId), paymentId, amount,
        "HELD", Instant.now()));
  }

  @Override
  public CardHoldResponse captureHold(String accountId, String holdId, String correlationId) {
    CardHoldResponse hold = find(holdId);
    if ("RELEASED".equals(hold.status())) {
      throw new PeerServiceException("CREDIT-CARD-SERVICE", 409,
          "HOLD_RELEASED", "Released hold cannot be captured");
    }
    CardHoldResponse captured = withStatus(hold, "CAPTURED");
    holdsByPayment.put(hold.referenceId(), captured);
    outstanding.compute(accountId, (ignored, current) ->
        (current == null ? new BigDecimal("50000.00") : current).add(hold.amount()));
    return captured;
  }

  @Override
  public CardHoldResponse releaseHold(String accountId, String holdId, String correlationId) {
    CardHoldResponse hold = find(holdId);
    if ("CAPTURED".equals(hold.status())) {
      throw new PeerServiceException("CREDIT-CARD-SERVICE", 409,
          "HOLD_CAPTURED", "Captured hold cannot be released");
    }
    CardHoldResponse released = withStatus(hold, "RELEASED");
    holdsByPayment.put(hold.referenceId(), released);
    return released;
  }

  @Override
  public CardAccountResponse payBill(String accountId, String paymentId, BigDecimal amount,
                                     String correlationId) {
    BigDecimal current = outstanding.computeIfAbsent(accountId,
        ignored -> new BigDecimal("50000.00"));
    if (amount.compareTo(current) > 0) {
      throw new PeerServiceException("CREDIT-CARD-SERVICE", 409,
          "PAYMENT_EXCEEDS_OUTSTANDING", "Paid amount exceeds outstanding amount");
    }
    BigDecimal remaining = current.subtract(amount);
    outstanding.put(accountId, remaining);
    BigDecimal sanctioned = new BigDecimal("100000.00");
    return new CardAccountResponse(accountId, "1001", 101L, "PLATINUM_CARD",
        "4000********9012", sanctioned, new BigDecimal("18.0000"),
        sanctioned.subtract(remaining), remaining, "ACTIVE", Instant.now());
  }

  private CardHoldResponse find(String holdId) {
    return holdsByPayment.values().stream()
        .filter(value -> String.valueOf(value.holdId()).equals(holdId)).findFirst()
        .orElseThrow(() -> new PeerServiceException("CREDIT-CARD-SERVICE", 404,
            "HOLD_NOT_FOUND", "Card hold not found"));
  }

  private CardHoldResponse withStatus(CardHoldResponse hold, String status) {
    return new CardHoldResponse(hold.holdId(), hold.accountId(), hold.referenceId(),
        hold.amount(), status, hold.createdAt());
  }
}
