package com.moneybags.payments.integration;

import com.moneybags.payments.dto.IntegrationDtos.*;

public interface DepositAccountClient {
  AccountEligibility eligibility(String accountId, String correlationId);
  ReservationResponse reserveBookTransfer(BookTransferReservationRequest request,
                                          String correlationId);
  DepositOperationResponse settleBookTransfer(String paymentId, String reservationId,
                                              String correlationId);
  ReservationResponse reserveCardRepayment(CardRepaymentReservationRequest request,
                                           String correlationId);
  DepositOperationResponse captureCardRepayment(String paymentId, String reservationId,
                                                String correlationId);
  DepositOperationResponse release(String reservationId, String paymentId,
                                   String reasonCode, String correlationId);
  DepositOperationResponse operationStatus(String paymentId, String correlationId);
  FixedDepositFundingReservationResponse reserveFixedDepositFunding(
      FixedDepositFundingReservationRequest request, String correlationId);
  FixedDepositFundingSettlementResponse settleFixedDepositFunding(
      String paymentId, FixedDepositFundingSettlementRequest request, String correlationId);
  FixedDepositPayoutConfirmationResponse confirmFixedDepositPayout(
      String fixedDepositId, FixedDepositPayoutConfirmationRequest request,
      String correlationId);
  FixedDepositFundingReservationResponse releaseFixedDepositFunding(
      String reservationId, String paymentId, String reasonCode, String correlationId);
}
