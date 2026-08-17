package com.moneybags.payments.integration.real;

import com.moneybags.payments.dto.IntegrationDtos.BillSummary;
import com.moneybags.payments.dto.IntegrationDtos.BillPaymentSettlementRequest;
import com.moneybags.payments.integration.BillGenerationClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oracle")
public class RealBillGenerationClient implements BillGenerationClient {
  private final RestClient client;

  public RealBillGenerationClient(@Qualifier("billingRestClient") RestClient client) {
    this.client = client;
  }

  @Override
  public BillSummary getBill(String billId, String correlationId) {
    return RealClientSupport.errors(client.get().uri("/internal/v1/bills/{id}", billId)
        .header("X-Correlation-Id", correlationId).retrieve(), "BILL-GENERATION-SERVICE")
        .body(BillSummary.class);
  }

  @Override
  public void recordPaymentSettlement(String billId, BillPaymentSettlementRequest request,
                                      String correlationId) {
    RealClientSupport.errors(client.post()
        .uri("/internal/v1/bills/{id}/payment-settlements", billId)
        .header("Idempotency-Key", "PAYMENT:" + request.paymentId() + ":BILLING")
        .header("X-Correlation-Id", correlationId)
        .body(request).retrieve(), "BILL-GENERATION-SERVICE")
        .toBodilessEntity();
  }
}
