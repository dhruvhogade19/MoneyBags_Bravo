package com.moneybags.payments.integration;

import com.moneybags.payments.dto.IntegrationDtos.*;
import java.math.BigDecimal;

public interface CreditCardClient {
  CardHoldResponse createHold(String accountId, String paymentId, BigDecimal amount,
                              String correlationId);
  CardHoldResponse captureHold(String accountId, String holdId, String correlationId);
  CardHoldResponse releaseHold(String accountId, String holdId, String correlationId);
  CardAccountResponse payBill(String accountId, String paymentId, BigDecimal amount,
                              String correlationId);
}
