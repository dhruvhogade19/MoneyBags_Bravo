package com.moneybags.payments.integration;

import com.moneybags.payments.dto.IntegrationDtos.*;

public interface AccountingClient {
  AccountingResponse postSettlement(AccountingSettlementRequest request,
                                    String idempotencyKey, String correlationId);
  AccountingResponse postFixedDeposit(FixedDepositAccountingRequest request,
                                      String idempotencyKey, String correlationId);
  AccountingLookupResponse findByReference(String externalReference, String correlationId);
  AccountingLookupResponse findFixedDepositByReference(String externalReference,
                                                       String correlationId);
  AccountingAccountClearanceResponse depositAccountClearance(
      String accountReference, String currencyCode, String correlationId);
  AccountingResponse reverse(String journalNumber, AccountingReversalRequest request,
                             String idempotencyKey, String correlationId);
}
