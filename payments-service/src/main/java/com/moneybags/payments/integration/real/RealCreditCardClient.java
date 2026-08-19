package com.moneybags.payments.integration.real;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.integration.CreditCardClient;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oracle")
public class RealCreditCardClient implements CreditCardClient {
  private final RestClient client;

  public RealCreditCardClient(@Qualifier("creditCardRestClient") RestClient client) {
    this.client = client;
  }

  @Override
  public CardHoldResponse createHold(String accountId, String paymentId, BigDecimal amount,
                                     String correlationId) {
    return post("/api/credit-cards/accounts/" + accountId + "/holds",
        new CardHoldRequest(paymentId, amount), paymentId, correlationId,
        CardHoldResponse.class);
  }

  @Override
  public CardHoldResponse captureHold(String accountId, String holdId, String correlationId) {
    return post("/api/credit-cards/accounts/" + accountId + "/holds/" + holdId + "/capture",
        null, "CARD-HOLD:" + holdId + ":CAPTURE", correlationId, CardHoldResponse.class);
  }

  @Override
  public CardHoldResponse releaseHold(String accountId, String holdId, String correlationId) {
    return post("/api/credit-cards/accounts/" + accountId + "/holds/" + holdId + "/release",
        null, "CARD-HOLD:" + holdId + ":RELEASE", correlationId, CardHoldResponse.class);
  }

  @Override
  public CardAccountResponse payBill(String accountId, String paymentId, BigDecimal amount,
                                     String correlationId) {
    return post("/api/credit-cards/accounts/" + accountId + "/payments/billpaid",
        new CardBillPaymentRequest(paymentId, amount), "PAYMENT:" + paymentId + ":BILLPAID",
        correlationId, CardAccountResponse.class);
  }

  private <T> T post(String uri, Object body, String key, String correlationId, Class<T> type) {
    RestClient.RequestBodySpec request = client.post().uri(uri)
        .header("Idempotency-Key", key).header("X-Correlation-Id", correlationId);
    if (body != null) {
      request.body(body);
    }
    return RealClientSupport.errors(request.retrieve(), "CREDIT-CARD-SERVICE").body(type);
  }
}
