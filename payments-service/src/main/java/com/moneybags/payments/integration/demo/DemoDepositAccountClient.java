package com.moneybags.payments.integration.demo;

import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.integration.DepositAccountClient;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"demo", "test"})
public class DemoDepositAccountClient implements DepositAccountClient {
  private final AtomicLong sequence = new AtomicLong(100);
  private final Map<String, ReservationResponse> reservations = new ConcurrentHashMap<>();
  private final Map<String, FixedDepositFundingReservationResponse> fdReservations =
      new ConcurrentHashMap<>();

  @Override
  public AccountEligibility eligibility(String accountId, String correlationId) {
    if (accountId.toLowerCase().contains("blocked")) {
      return new AccountEligibility(false, false, "BLOCKED", "Demo account is blocked");
    }
    return new AccountEligibility(true, true, "ACTIVE", "Eligible");
  }

  @Override
  public ReservationResponse reserveBookTransfer(BookTransferReservationRequest request,
                                                 String correlationId) {
    rejectInsufficient(request.sourceAccountId(), request.amount().doubleValue());
    return reserve(request.paymentId(), request.expiresAt());
  }

  @Override
  public DepositOperationResponse settleBookTransfer(String paymentId, String reservationId,
                                                     String correlationId) {
    capture(reservationId);
    return new DepositOperationResponse(paymentId, reservationId, "SETTLED",
        "DEP-DEBIT-" + paymentId, "DEP-CREDIT-" + paymentId);
  }

  @Override
  public ReservationResponse reserveCardRepayment(CardRepaymentReservationRequest request,
                                                  String correlationId) {
    rejectInsufficient(request.sourceAccountId(), request.amount().doubleValue());
    return reserve(request.paymentId(), request.expiresAt());
  }

  @Override
  public DepositOperationResponse captureCardRepayment(String paymentId, String reservationId,
                                                       String correlationId) {
    capture(reservationId);
    return new DepositOperationResponse(paymentId, reservationId, "CAPTURED",
        "DEP-DEBIT-" + paymentId, null);
  }

  @Override
  public DepositOperationResponse release(String reservationId, String paymentId,
                                          String reasonCode, String correlationId) {
    ReservationResponse current = require(reservationId);
    if ("CAPTURED".equals(current.status())) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 409,
          "RESERVATION_ALREADY_CAPTURED", "Captured reservation cannot be released");
    }
    reservations.put(reservationId, new ReservationResponse(reservationId,
        current.paymentId(), "RELEASED", current.expiresAt()));
    return new DepositOperationResponse(paymentId, reservationId, "RELEASED", null, null);
  }

  @Override
  public DepositOperationResponse operationStatus(String paymentId, String correlationId) {
    return reservations.values().stream().filter(value -> value.paymentId().equals(paymentId))
        .findFirst().map(value -> new DepositOperationResponse(paymentId,
            value.reservationId(), value.status(), null, null))
        .orElseThrow(() -> new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 404,
            "OPERATION_NOT_FOUND", "No demo deposit operation found"));
  }

  @Override
  public FixedDepositFundingReservationResponse reserveFixedDepositFunding(
      FixedDepositFundingReservationRequest request, String correlationId) {
    rejectInsufficient(request.sourceAccountId(), request.amount().doubleValue());
    return fdReservations.values().stream()
        .filter(value -> value.paymentId().equals(request.paymentId()))
        .findFirst().orElseGet(() -> {
          String reservationId = "fd-reservation-" + sequence.incrementAndGet();
          FixedDepositFundingReservationResponse response =
              new FixedDepositFundingReservationResponse(reservationId, request.paymentId(),
                  "FIXED_DEPOSIT_FUNDING", "ACTIVE", request.sourceAccountId(),
                  "fd-account-" + request.fixedDepositId(), request.fixedDepositId(),
                  request.amount(), request.currencyCode(), request.expiresAt());
          fdReservations.put(reservationId, response);
          return response;
        });
  }

  @Override
  public FixedDepositFundingSettlementResponse settleFixedDepositFunding(
      String paymentId, FixedDepositFundingSettlementRequest request, String correlationId) {
    FixedDepositFundingReservationResponse reservation = requireFd(request.reservationId());
    if ("RELEASED".equals(reservation.status())) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 409,
          "RESERVATION_RELEASED", "Released FD reservation cannot be settled");
    }
    fdReservations.put(request.reservationId(), new FixedDepositFundingReservationResponse(
        reservation.reservationId(), reservation.paymentId(), reservation.operationType(),
        "SETTLED", reservation.sourceAccountId(), reservation.targetAccountId(),
        reservation.fixedDepositId(), reservation.amount(), reservation.currencyCode(),
        reservation.expiresAt()));
    return new FixedDepositFundingSettlementResponse(request.reservationId(), paymentId,
        "FIXED_DEPOSIT_FUNDING", "SETTLED", request.fixedDepositId(), "ACTIVE",
        List.of("txn-fd-funding-debit", "txn-fd-funding-credit"));
  }

  @Override
  public FixedDepositPayoutConfirmationResponse confirmFixedDepositPayout(
      String fixedDepositId, FixedDepositPayoutConfirmationRequest request,
      String correlationId) {
    return new FixedDepositPayoutConfirmationResponse(fixedDepositId, request.paymentId(),
        "PAID_OUT", request.payoutAccountId(), request.netPayoutAmount(),
        request.currencyCode(), Instant.now());
  }

  @Override
  public FixedDepositFundingReservationResponse releaseFixedDepositFunding(
      String reservationId, String paymentId, String reasonCode, String correlationId) {
    FixedDepositFundingReservationResponse current = requireFd(reservationId);
    if ("SETTLED".equals(current.status())) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 409,
          "RESERVATION_ALREADY_SETTLED", "Settled FD reservation cannot be released");
    }
    FixedDepositFundingReservationResponse released =
        new FixedDepositFundingReservationResponse(current.reservationId(), current.paymentId(),
            current.operationType(), "RELEASED", current.sourceAccountId(),
            current.targetAccountId(), current.fixedDepositId(), current.amount(),
            current.currencyCode(), current.expiresAt());
    fdReservations.put(reservationId, released);
    return released;
  }

  private FixedDepositFundingReservationResponse requireFd(String reservationId) {
    FixedDepositFundingReservationResponse current = fdReservations.get(reservationId);
    if (current == null) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 404,
          "RESERVATION_NOT_FOUND", "FD reservation not found");
    }
    return current;
  }

  private ReservationResponse reserve(String paymentId, Instant expiresAt) {
    return reservations.values().stream().filter(value -> value.paymentId().equals(paymentId))
        .findFirst().orElseGet(() -> {
          String id = "reservation-" + sequence.incrementAndGet();
          ReservationResponse result = new ReservationResponse(id, paymentId, "HELD", expiresAt);
          reservations.put(id, result);
          return result;
        });
  }

  private void capture(String reservationId) {
    ReservationResponse current = require(reservationId);
    if ("RELEASED".equals(current.status())) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 409,
          "RESERVATION_RELEASED", "Released reservation cannot be captured");
    }
    reservations.put(reservationId, new ReservationResponse(reservationId,
        current.paymentId(), "CAPTURED", current.expiresAt()));
  }

  private ReservationResponse require(String reservationId) {
    ReservationResponse current = reservations.get(reservationId);
    if (current == null) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 404,
          "RESERVATION_NOT_FOUND", "Reservation not found");
    }
    return current;
  }

  private void rejectInsufficient(String accountId, double amount) {
    if (accountId.toLowerCase().contains("insufficient") || amount > 1_000_000) {
      throw new PeerServiceException("DEPOSIT-ACCOUNT-SERVICE", 409,
          "INSUFFICIENT_FUNDS", "Insufficient available balance");
    }
  }
}
