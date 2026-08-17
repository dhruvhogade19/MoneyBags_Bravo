package com.moneybags.payments.integration.real;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.integration.DepositAccountClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Profile("oracle")
public class RealDepositAccountClient implements DepositAccountClient {
  private final RestClient client;

  public RealDepositAccountClient(@Qualifier("depositRestClient") RestClient client) {
    this.client = client;
  }

  @Override
  public AccountEligibility eligibility(String accountId, String correlationId) {
    return RealClientSupport.errors(client.get()
        .uri("/api/internal/deposit-accounts/{id}/eligibility", accountId)
        .header("X-Correlation-Id", correlationId).retrieve(), "DEPOSIT-ACCOUNT-SERVICE")
        .body(AccountEligibility.class);
  }

  @Override
  public ReservationResponse reserveBookTransfer(BookTransferReservationRequest request,
                                                 String correlationId) {
    return post("/api/internal/deposit-payment-operations/book-transfers/reservations",
        request, depositKey(request.paymentId(), "BOOK_TRANSFER_RESERVE"), correlationId,
        ReservationResponse.class);
  }

  @Override
  public DepositOperationResponse settleBookTransfer(String paymentId, String reservationId,
                                                     String correlationId) {
    return post("/api/internal/deposit-payment-operations/book-transfers/" + paymentId
        + "/settle", new ReservationCommand(reservationId),
        depositKey(paymentId, "BOOK_TRANSFER_SETTLE"), correlationId,
        DepositOperationResponse.class);
  }

  @Override
  public ReservationResponse reserveCardRepayment(CardRepaymentReservationRequest request,
                                                  String correlationId) {
    return post("/api/internal/deposit-payment-operations/credit-card-repayments/reservations",
        request, depositKey(request.paymentId(), "CARD_REPAYMENT_RESERVE"), correlationId,
        ReservationResponse.class);
  }

  @Override
  public DepositOperationResponse captureCardRepayment(String paymentId, String reservationId,
                                                       String correlationId) {
    return post("/api/internal/deposit-payment-operations/credit-card-repayments/" + paymentId
        + "/capture", new ReservationCommand(reservationId),
        depositKey(paymentId, "CARD_REPAYMENT_CAPTURE"), correlationId,
        DepositOperationResponse.class);
  }

  @Override
  public DepositOperationResponse release(String reservationId, String paymentId,
                                          String reasonCode, String correlationId) {
    return post("/api/internal/deposit-payment-operations/reservations/" + reservationId
        + "/release", new ReleaseReservationRequest(paymentId, reasonCode),
        depositKey(paymentId, "RESERVATION_RELEASE"), correlationId,
        DepositOperationResponse.class);
  }

  @Override
  public DepositOperationResponse operationStatus(String paymentId, String correlationId) {
    return RealClientSupport.errors(client.get()
        .uri("/api/internal/deposit-payment-operations/{id}", paymentId)
        .header("X-Correlation-Id", correlationId).retrieve(), "DEPOSIT-ACCOUNT-SERVICE")
        .body(DepositOperationResponse.class);
  }

  @Override
  public FixedDepositFundingReservationResponse reserveFixedDepositFunding(
      FixedDepositFundingReservationRequest request, String correlationId) {
    return post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/reservations",
        request, depositKey(request.paymentId(), "FD_FUNDING_RESERVE"), correlationId,
        FixedDepositFundingReservationResponse.class);
  }

  @Override
  public FixedDepositFundingSettlementResponse settleFixedDepositFunding(
      String paymentId, FixedDepositFundingSettlementRequest request, String correlationId) {
    return post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/" + paymentId
        + "/settle", request, depositKey(paymentId, "FD_FUNDING_SETTLE"), correlationId,
        FixedDepositFundingSettlementResponse.class);
  }

  @Override
  public FixedDepositPayoutConfirmationResponse confirmFixedDepositPayout(
      String fixedDepositId, FixedDepositPayoutConfirmationRequest request,
      String correlationId) {
    return post("/internal/v1/deposit-accounts/fixed-deposits/" + fixedDepositId
        + "/payout-confirmations", request,
        depositKey(request.paymentId(), "FD_PAYOUT_CONFIRM"), correlationId,
        FixedDepositPayoutConfirmationResponse.class);
  }

  @Override
  public FixedDepositFundingReservationResponse releaseFixedDepositFunding(
      String reservationId, String paymentId, String reasonCode, String correlationId) {
    return post("/internal/v1/deposit-payment-operations/reservations/" + reservationId
        + "/release", new ReleaseReservationRequest(paymentId, reasonCode),
        depositKey(paymentId, "FD_FUNDING_RELEASE"), correlationId,
        FixedDepositFundingReservationResponse.class);
  }

  private <T> T post(String uri, Object body, String idempotencyKey, String correlationId,
                     Class<T> type) {
    return RealClientSupport.errors(client.post().uri(uri)
        .header("Idempotency-Key", idempotencyKey)
        .header("X-Correlation-Id", correlationId).body(body).retrieve(),
        "DEPOSIT-ACCOUNT-SERVICE").body(type);
  }

  private String depositKey(String paymentId, String operation) {
    return "PAYMENT:" + paymentId + ":DEPOSIT:" + operation;
  }
}
