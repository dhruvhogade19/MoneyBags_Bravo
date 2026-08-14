package com.moneybags.payments.integration.demo;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.integration.AccountingClient;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoAccountingClient implements AccountingClient {
  private final AtomicLong sequence = new AtomicLong(1);
  private final Map<String, AccountingResponse> byReference = new ConcurrentHashMap<>();

  @Override
  public AccountingResponse postSettlement(AccountingSettlementRequest request,
                                           String idempotencyKey, String correlationId) {
    if (request.reference() != null && request.reference().toLowerCase().contains("accounting-fail")) {
      throw new PeerServiceException("ACCOUNTING-SERVICE", 422,
          "ACCOUNTING_RULE_NOT_FOUND", "Demo Accounting rejected the posting");
    }
    String externalReference = "PAYMENT:" + request.paymentId() + ":ACCOUNTING";
    return byReference.computeIfAbsent(externalReference, ignored -> new AccountingResponse(
        "JRN-DEMO-" + String.format("%06d", sequence.getAndIncrement()), sequence.get(),
        externalReference, "PAYMENTS-SERVICE", request.paymentType(), request.occurredAt(),
        request.businessDate(), request.currencyCode(), "POSTED", request.amount(),
        request.amount(), correlationId, Instant.now(), false, null));
  }

  @Override
  public AccountingResponse postFixedDeposit(FixedDepositAccountingRequest request,
                                             String idempotencyKey, String correlationId) {
    return byReference.computeIfAbsent(request.postingReference(), ignored -> {
      java.math.BigDecimal total = request.components().stream()
          .map(FixedDepositAccountingComponent::amount)
          .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
      return new AccountingResponse(
          "JRN-DEMO-FD-" + String.format("%06d", sequence.getAndIncrement()), sequence.get(),
          request.postingReference(), "PAYMENTS-SERVICE", "FD_" + request.postingType(),
          request.occurredAt().toInstant(), request.businessDate(), request.currencyCode(),
          "POSTED", total, total, correlationId, Instant.now(), false, null);
    });
  }

  @Override
  public AccountingLookupResponse findByReference(String externalReference,
                                                  String correlationId) {
    AccountingResponse response = byReference.get(externalReference);
    if (response == null) {
      throw new PeerServiceException("ACCOUNTING-SERVICE", 404,
          "POSTING_REQUEST_NOT_FOUND", "No demo posting exists for the reference");
    }
    return new AccountingLookupResponse(externalReference, "POSTED", response.journalNumber(),
        response.occurredAt(), response.postedAt(), null, null, response);
  }

  @Override
  public AccountingLookupResponse findFixedDepositByReference(String externalReference,
                                                              String correlationId) {
    return findByReference(externalReference, correlationId);
  }

  @Override
  public AccountingResponse reverse(String journalNumber, AccountingReversalRequest request,
                                    String idempotencyKey, String correlationId) {
    String externalReference = "PAYMENT:" + request.paymentId() + ":REVERSAL";
    return byReference.computeIfAbsent(externalReference, ignored -> new AccountingResponse(
        "JRN-DEMO-REV-" + String.format("%06d", sequence.getAndIncrement()), sequence.get(),
        externalReference, "PAYMENTS-SERVICE", "JOURNAL_REVERSAL", request.occurredAt(),
        request.businessDate(), "INR", "REVERSAL", null, null, correlationId,
        Instant.now(), false, journalNumber));
  }
}
