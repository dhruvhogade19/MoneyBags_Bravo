package com.moneybags.payments.integration.real;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.integration.AccountingClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oracle")
public class RealAccountingClient implements AccountingClient {
  private final RestClient client;
  private final String token;

  public RealAccountingClient(@Qualifier("accountingRestClient") RestClient client,
                              @Value("${clients.accounting.service-token:service-token}")
                              String token) {
    this.client = client;
    this.token = token;
  }

  @Override
  public AccountingResponse postSettlement(AccountingSettlementRequest request,
                                           String idempotencyKey, String correlationId) {
    return post("/internal/v1/payment-postings/settlements", request, idempotencyKey,
        correlationId, AccountingResponse.class);
  }

  @Override
  public AccountingResponse postFixedDeposit(FixedDepositAccountingRequest request,
                                             String idempotencyKey, String correlationId) {
    return post("/internal/v1/fixed-deposit-postings", request, idempotencyKey,
        correlationId, AccountingResponse.class);
  }

  @Override
  public AccountingLookupResponse findByReference(String externalReference,
                                                  String correlationId) {
    return RealClientSupport.errors(client.get()
        .uri("/internal/v1/payment-postings/by-reference/{reference}", externalReference)
        .header("Authorization", "Bearer " + token)
        .header("X-Correlation-Id", correlationId).retrieve(), "ACCOUNTING-SERVICE")
        .body(AccountingLookupResponse.class);
  }

  @Override
  public AccountingLookupResponse findFixedDepositByReference(String externalReference,
                                                              String correlationId) {
    return RealClientSupport.errors(client.get()
        .uri("/internal/v1/fixed-deposit-postings/by-reference/{reference}", externalReference)
        .header("Authorization", "Bearer " + token)
        .header("X-Correlation-Id", correlationId).retrieve(), "ACCOUNTING-SERVICE")
        .body(AccountingLookupResponse.class);
  }

  @Override
  public AccountingResponse reverse(String journalNumber, AccountingReversalRequest request,
                                    String idempotencyKey, String correlationId) {
    return post("/internal/v1/journals/" + journalNumber + "/reversals", request,
        idempotencyKey, correlationId, AccountingResponse.class);
  }

  private <T> T post(String uri, Object body, String key, String correlationId, Class<T> type) {
    return RealClientSupport.errors(client.post().uri(uri)
        .header("Authorization", "Bearer " + token)
        .header("Idempotency-Key", key)
        .header("X-Correlation-Id", correlationId).body(body).retrieve(),
        "ACCOUNTING-SERVICE").body(type);
  }
}
