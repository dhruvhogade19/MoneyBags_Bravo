package com.moneybags.payments.integration;

import com.moneybags.payments.dto.IntegrationDtos.BillSummary;
import com.moneybags.payments.dto.IntegrationDtos.BillPaymentSettlementRequest;

public interface BillGenerationClient {
  BillSummary getBill(String billId, String correlationId);

  void recordPaymentSettlement(String billId, BillPaymentSettlementRequest request,
                               String correlationId);
}
