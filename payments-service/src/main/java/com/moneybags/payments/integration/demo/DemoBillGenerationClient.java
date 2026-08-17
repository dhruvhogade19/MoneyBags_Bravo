package com.moneybags.payments.integration.demo;

import com.moneybags.payments.dto.IntegrationDtos.BillSummary;
import com.moneybags.payments.dto.IntegrationDtos.BillPaymentSettlementRequest;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.integration.BillGenerationClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoBillGenerationClient implements BillGenerationClient {
  private final Set<String> recordedPayments = ConcurrentHashMap.newKeySet();

  @Override
  public BillSummary getBill(String billId, String correlationId) {
    if (billId.toLowerCase().contains("missing")) {
      throw new PeerServiceException("BILL-GENERATION-SERVICE", 404,
          "BILL_NOT_FOUND", "Demo bill was not found");
    }
    return new BillSummary(billId, "101", "2026-08", "GENERATED",
        new BigDecimal("10000.00"), new BigDecimal("50000.00"),
        new BigDecimal("2500.00"), BigDecimal.ZERO,
        new BigDecimal("50000.00"), LocalDate.of(2026, 8, 28), "INR", List.of());
  }

  @Override
  public void recordPaymentSettlement(String billId, BillPaymentSettlementRequest request,
                                      String correlationId) {
    if (billId.toLowerCase().contains("callback-fail")) {
      throw new PeerServiceException("BILL-GENERATION-SERVICE", 503,
          "BILLING_CALLBACK_UNAVAILABLE", "Demo Billing callback is unavailable");
    }
    recordedPayments.add(request.paymentId());
  }
}
