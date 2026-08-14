package com.moneybags.payments.service;

import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.dto.IntegrationDtos.*;
import com.moneybags.payments.dto.PaymentDtos.*;
import com.moneybags.payments.exception.BusinessValidationException;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.exception.ResourceNotFoundException;
import com.moneybags.payments.integration.*;
import com.moneybags.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentOrchestrationService {
  private static final String ACCOUNTING = "ACCOUNTING-SERVICE";
  private static final String DEPOSIT = "DEPOSIT-ACCOUNT-SERVICE";
  private static final String CARD = "CREDIT-CARD-SERVICE";

  private final PaymentRepository payments;
  private final PaymentSupportService support;
  private final EodControlService eod;
  private final DepositAccountClient deposit;
  private final CreditCardClient cards;
  private final AccountingClient accounting;
  private final BillGenerationClient billing;
  private final NotificationClient notifications;

  public PaymentOrchestrationService(PaymentRepository payments, PaymentSupportService support,
      EodControlService eod, DepositAccountClient deposit, CreditCardClient cards,
      AccountingClient accounting, BillGenerationClient billing,
      NotificationClient notifications) {
    this.payments = payments;
    this.support = support;
    this.eod = eod;
    this.deposit = deposit;
    this.cards = cards;
    this.accounting = accounting;
    this.billing = billing;
    this.notifications = notifications;
  }

  public PaymentResponse bookTransfer(BookTransferRequest request, String key,
                                      String correlationId) {
    eod.assertOpen();
    if (request.sourceAccountId().equals(request.targetAccountId())) {
      throw new BusinessValidationException("Source and target accounts must be different");
    }
    String fingerprint = PaymentSupportService.fingerprint("BOOK_TRANSFER", request);
    Optional<PaymentResponse> existing = support.existing(request.requestorCustomerId(), key,
        fingerprint);
    if (existing.isPresent()) return existing.get();

    Payment payment = newPayment(request.requestorCustomerId(), key, fingerprint,
        PaymentType.BOOK_TRANSFER, InstrumentType.DEPOSIT_ACCOUNT, request.sourceAccountId(),
        InstrumentType.DEPOSIT_ACCOUNT, request.targetAccountId(), null, null,
        request.amount(), request.currencyCode(), request.reference(), correlationId);
    support.initial(payment);

    try {
      AccountEligibility source = support.attempt(payment, "DEPOSIT_ELIGIBILITY_SOURCE", DEPOSIT,
          () -> deposit.eligibility(request.sourceAccountId(), correlationId));
      AccountEligibility target = support.attempt(payment, "DEPOSIT_ELIGIBILITY_TARGET", DEPOSIT,
          () -> deposit.eligibility(request.targetAccountId(), correlationId));
      if (!source.debitAllowed() || !target.creditAllowed()) {
        throw new BusinessValidationException("Source debit or target credit is not allowed");
      }

      support.transition(payment, PaymentStatus.PENDING_RESERVATION,
          "ACCOUNTS_ELIGIBLE", null);
      ReservationResponse reservation = support.attempt(payment, "DEPOSIT_RESERVE", DEPOSIT,
          () -> deposit.reserveBookTransfer(new BookTransferReservationRequest(
              payment.getPaymentId(), request.requestorCustomerId(), request.sourceAccountId(),
              request.targetAccountId(), request.amount(), request.currencyCode(),
              Instant.now().plusSeconds(300)), correlationId));
      payment.setDepositReservationId(reservation.reservationId());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_ACCOUNTING,
          "FUNDS_RESERVED", null);
      AccountingResponse journal = postAccounting(payment);
      payment.setAccountingJournalNumber(journal.journalNumber());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_SETTLEMENT,
          "ACCOUNTING_POSTED", null);
      support.attempt(payment, "DEPOSIT_SETTLE", DEPOSIT,
          () -> deposit.settleBookTransfer(payment.getPaymentId(),
              payment.getDepositReservationId(), correlationId));
      support.transition(payment, PaymentStatus.SETTLED, "TRANSFER_SETTLED", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException failure) {
      compensateBookTransfer(payment, failure);
    }
    return PaymentSupportService.response(payment);
  }

  public PaymentResponse fixedDepositFunding(FixedDepositFundingRequest request, String key,
                                              String correlationId) {
    eod.assertOpen();
    String fingerprint = PaymentSupportService.fingerprint("FIXED_DEPOSIT_FUNDING", request);
    Optional<PaymentResponse> existing = support.existing(request.requestorCustomerId(), key,
        fingerprint);
    if (existing.isPresent()) return existing.get();

    Payment payment = newPayment(request.requestorCustomerId(), key, fingerprint,
        PaymentType.FIXED_DEPOSIT_FUNDING, InstrumentType.DEPOSIT_ACCOUNT,
        request.sourceAccountId(), InstrumentType.FIXED_DEPOSIT_ACCOUNT,
        request.fixedDepositId(), null, null, request.amount(), request.currencyCode(),
        request.reference(), correlationId);
    payment.setFixedDepositId(request.fixedDepositId());
    payment.setPrincipalAmount(request.amount());
    payment.setInterestAmount(BigDecimal.ZERO);
    support.initial(payment);

    try {
      AccountEligibility source = support.attempt(payment, "DEPOSIT_ELIGIBILITY_SOURCE", DEPOSIT,
          () -> deposit.eligibility(request.sourceAccountId(), correlationId));
      if (!source.debitAllowed()) {
        throw new BusinessValidationException("Source deposit account does not allow debit");
      }

      support.transition(payment, PaymentStatus.PENDING_RESERVATION,
          "SOURCE_ACCOUNT_ELIGIBLE", null);
      FixedDepositFundingReservationResponse reservation = support.attempt(payment,
          "FD_FUNDING_RESERVE", DEPOSIT,
          () -> deposit.reserveFixedDepositFunding(new FixedDepositFundingReservationRequest(
              payment.getPaymentId(), request.requestorCustomerId(), request.sourceAccountId(),
              request.fixedDepositId(), request.amount(), request.currencyCode(),
              Instant.now().plusSeconds(300)), correlationId));
      payment.setDepositReservationId(reservation.reservationId());
      if (reservation.targetAccountId() != null) {
        payment.setDestinationAccountId(reservation.targetAccountId());
      }
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_ACCOUNTING,
          "FD_FUNDING_RESERVED", null);
      AccountingResponse journal = postFixedDepositAccounting(payment);
      payment.setAccountingJournalNumber(journal.journalNumber());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_SETTLEMENT,
          "ACCOUNTING_POSTED", null);
      support.attempt(payment, "FD_FUNDING_SETTLE", DEPOSIT,
          () -> deposit.settleFixedDepositFunding(payment.getPaymentId(),
              new FixedDepositFundingSettlementRequest(payment.getDepositReservationId(),
                  payment.getFixedDepositId(), payment.getAccountingJournalNumber()),
              correlationId));
      support.transition(payment, PaymentStatus.SETTLED, "FIXED_DEPOSIT_ACTIVATED", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException failure) {
      compensateFixedDepositFunding(payment, failure);
    }
    return PaymentSupportService.response(payment);
  }

  public PaymentResponse fixedDepositPayout(FixedDepositPayoutRequest request, String key,
                                            String correlationId) {
    String fingerprint = PaymentSupportService.fingerprint("FIXED_DEPOSIT_PAYOUT", request);
    Optional<PaymentResponse> existing = support.existing(request.requestorCustomerId(), key,
        fingerprint);
    if (existing.isPresent()) return existing.get();

    Payment payment = newPayment(request.requestorCustomerId(), key, fingerprint,
        request.paymentType(), InstrumentType.FIXED_DEPOSIT_ACCOUNT, request.sourceAccountId(),
        request.destinationType(), request.destinationAccountId(), null, null, request.amount(),
        request.currencyCode(), request.reference(), correlationId);
    payment.setFixedDepositId(request.fixedDepositId());
    payment.setPrincipalAmount(request.principalAmount());
    payment.setInterestAmount(request.interestAmount());
    support.initial(payment);

    try {
      validateFixedDepositPayout(request);
      AccountEligibility destination = support.attempt(payment,
          "DEPOSIT_ELIGIBILITY_TARGET", DEPOSIT,
          () -> deposit.eligibility(request.destinationAccountId(), correlationId));
      if (!destination.creditAllowed()) {
        throw new BusinessValidationException("Payout deposit account does not allow credit");
      }

      support.transition(payment, PaymentStatus.PENDING_ACCOUNTING,
          "FD_PAYOUT_VALIDATED", null);
      AccountingResponse journal = postFixedDepositAccounting(payment);
      payment.setAccountingJournalNumber(journal.journalNumber());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_SETTLEMENT,
          "ACCOUNTING_POSTED", null);
      support.attempt(payment, "FD_PAYOUT_CONFIRM", DEPOSIT,
          () -> deposit.confirmFixedDepositPayout(payment.getFixedDepositId(),
              new FixedDepositPayoutConfirmationRequest(payment.getPaymentId(),
                  payment.getAccountingJournalNumber(), payment.getDestinationAccountId(),
                  payment.getPrincipalAmount(), payment.getInterestAmount(), payment.getAmount(),
                  payment.getCurrencyCode(), fdPayoutType(payment.getPaymentType())),
              correlationId));
      support.transition(payment, PaymentStatus.SETTLED, "FIXED_DEPOSIT_PAID_OUT", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException failure) {
      compensateFixedDepositPayout(payment, failure);
    }
    return PaymentSupportService.response(payment);
  }

  public PaymentResponse merchantPayment(MerchantPaymentRequest request, String key,
                                         String correlationId) {
    eod.assertOpen();
    String fingerprint = PaymentSupportService.fingerprint("MERCHANT_PAYMENT", request);
    Optional<PaymentResponse> existing = support.existing(request.requestorCustomerId(), key,
        fingerprint);
    if (existing.isPresent()) return existing.get();

    Payment payment = newPayment(request.requestorCustomerId(), key, fingerprint,
        PaymentType.CREDIT_CARD_MERCHANT_PAYMENT, InstrumentType.CREDIT_CARD_ACCOUNT,
        request.creditCardAccountId(), InstrumentType.MERCHANT, request.merchantId(),
        request.merchantId(), null, request.amount(), request.currencyCode(), request.reference(),
        correlationId);
    support.initial(payment);

    try {
      support.transition(payment, PaymentStatus.PENDING_RESERVATION,
          "CARD_VALIDATION_STARTED", null);
      CardHoldResponse hold = support.attempt(payment, "CARD_HOLD", CARD,
          () -> cards.createHold(request.creditCardAccountId(), payment.getPaymentId(),
              request.amount(), correlationId));
      payment.setCardHoldId(String.valueOf(hold.holdId()));
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_ACCOUNTING, "CARD_LIMIT_HELD", null);
      AccountingResponse journal = postAccounting(payment);
      payment.setAccountingJournalNumber(journal.journalNumber());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_SETTLEMENT,
          "ACCOUNTING_POSTED", null);
      support.attempt(payment, "CARD_CAPTURE", CARD,
          () -> cards.captureHold(request.creditCardAccountId(), payment.getCardHoldId(),
              correlationId));
      support.transition(payment, PaymentStatus.SETTLED, "CARD_HOLD_CAPTURED", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException failure) {
      compensateMerchant(payment, failure);
    }
    return PaymentSupportService.response(payment);
  }

  public PaymentResponse cardRepayment(CardRepaymentRequest request, String key,
                                       String correlationId) {
    eod.assertOpen();
    String fingerprint = PaymentSupportService.fingerprint("CARD_REPAYMENT", request);
    Optional<PaymentResponse> existing = support.existing(request.requestorCustomerId(), key,
        fingerprint);
    if (existing.isPresent()) return existing.get();

    Payment payment = newPayment(request.requestorCustomerId(), key, fingerprint,
        PaymentType.CREDIT_CARD_REPAYMENT, InstrumentType.DEPOSIT_ACCOUNT,
        request.sourceDepositAccountId(), InstrumentType.CREDIT_CARD_ACCOUNT,
        request.creditCardAccountId(), null, request.billId(), request.amount(),
        request.currencyCode(), request.reference(), correlationId);
    support.initial(payment);
    boolean depositCaptured = false;

    try {
      BillSummary bill = support.attempt(payment, "BILL_LOOKUP", "BILL-GENERATION-SERVICE",
          () -> billing.getBill(request.billId(), correlationId));
      validateBill(request, bill);
      AccountEligibility source = support.attempt(payment, "DEPOSIT_ELIGIBILITY_SOURCE", DEPOSIT,
          () -> deposit.eligibility(request.sourceDepositAccountId(), correlationId));
      if (!source.debitAllowed()) {
        throw new BusinessValidationException("Source deposit account does not allow debit");
      }

      support.transition(payment, PaymentStatus.PENDING_RESERVATION,
          "BILL_AND_ACCOUNT_VALIDATED", null);
      ReservationResponse reservation = support.attempt(payment, "DEPOSIT_RESERVE", DEPOSIT,
          () -> deposit.reserveCardRepayment(new CardRepaymentReservationRequest(
              payment.getPaymentId(), request.requestorCustomerId(),
              request.sourceDepositAccountId(), request.creditCardAccountId(), request.amount(),
              request.currencyCode(), Instant.now().plusSeconds(300)), correlationId));
      payment.setDepositReservationId(reservation.reservationId());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_ACCOUNTING, "FUNDS_RESERVED", null);
      AccountingResponse journal = postAccounting(payment);
      payment.setAccountingJournalNumber(journal.journalNumber());
      payments.save(payment);

      support.transition(payment, PaymentStatus.PENDING_SETTLEMENT,
          "ACCOUNTING_POSTED", null);
      support.attempt(payment, "DEPOSIT_CAPTURE", DEPOSIT,
          () -> deposit.captureCardRepayment(payment.getPaymentId(),
              payment.getDepositReservationId(), correlationId));
      depositCaptured = true;
      support.attempt(payment, "CARD_BILLPAID", CARD,
          () -> cards.payBill(request.creditCardAccountId(), payment.getPaymentId(),
              request.amount(), correlationId));

      payment.setBillingSettlementAt(Instant.now());
      payments.save(payment);
      support.transition(payment, PaymentStatus.PENDING_BILLING,
          "FINANCIAL_SETTLEMENT_COMPLETED", null);
      try {
        recordBillSettlement(payment);
      } catch (RuntimeException callbackFailure) {
        rememberFailure(payment, callbackFailure);
        support.transition(payment, PaymentStatus.PENDING_BILLING,
            payment.getFailureCode(), payment.getFailureMessage());
        return PaymentSupportService.response(payment);
      }
      support.transition(payment, PaymentStatus.SETTLED, "CARD_REPAYMENT_APPLIED", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException failure) {
      compensateRepayment(payment, failure, depositCaptured);
    }
    return PaymentSupportService.response(payment);
  }

  private AccountingResponse postAccounting(Payment payment) {
    AccountingSettlementRequest request = new AccountingSettlementRequest(payment.getPaymentId(),
        payment.getPaymentType().name(),
        new AccountingInstrument(payment.getSourceInstrumentType().name(),
            payment.getSourceAccountId()),
        new AccountingInstrument(payment.getDestinationInstrumentType().name(),
            payment.getDestinationAccountId()), payment.getAmount(), payment.getCurrencyCode(),
        Instant.now(), payment.getBusinessDate(), payment.getReference());
    String key = "PAYMENT:" + payment.getPaymentId() + ":ACCOUNTING";
    try {
      return support.attempt(payment, "ACCOUNTING_POST", ACCOUNTING,
          () -> accounting.postSettlement(request, key, payment.getCorrelationId()));
    } catch (PeerServiceException exception) {
      if (exception.getStatus() != 408 && exception.getStatus() != 504) throw exception;
      AccountingLookupResponse lookup = support.attempt(payment, "ACCOUNTING_LOOKUP", ACCOUNTING,
          () -> accounting.findByReference(key, payment.getCorrelationId()));
      if (!"POSTED".equals(lookup.outcome()) || lookup.journalNumber() == null) throw exception;
      if (lookup.journal() != null) return lookup.journal();
      return new AccountingResponse(lookup.journalNumber(), null, key, "PAYMENTS-SERVICE",
          payment.getPaymentType().name(), Instant.now(), payment.getBusinessDate(),
          payment.getCurrencyCode(), "POSTED", payment.getAmount(), payment.getAmount(),
          payment.getCorrelationId(), lookup.completedAt(), true, null);
    }
  }

  private AccountingResponse postFixedDepositAccounting(Payment payment) {
    String postingType;
    List<FixedDepositAccountingComponent> components = new ArrayList<>();
    if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING) {
      postingType = "FUNDING";
      components.add(new FixedDepositAccountingComponent("PRINCIPAL", payment.getAmount()));
    } else if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT) {
      postingType = "MATURITY_PAYOUT";
      components.add(new FixedDepositAccountingComponent("PRINCIPAL",
          payment.getPrincipalAmount()));
      components.add(new FixedDepositAccountingComponent("INTEREST",
          payment.getInterestAmount()));
    } else {
      postingType = "PREMATURE_CLOSURE";
      components.add(new FixedDepositAccountingComponent("PRINCIPAL",
          payment.getPrincipalAmount()));
      components.add(new FixedDepositAccountingComponent("ELIGIBLE_INTEREST",
          payment.getInterestAmount()));
      components.add(new FixedDepositAccountingComponent("INTEREST_ADJUSTMENT", BigDecimal.ZERO));
      components.add(new FixedDepositAccountingComponent("PENALTY", BigDecimal.ZERO));
      components.add(new FixedDepositAccountingComponent("TAX", BigDecimal.ZERO));
      components.add(new FixedDepositAccountingComponent("NET_PAYOUT", payment.getAmount()));
    }

    String key = "PAYMENT:" + payment.getPaymentId() + ":ACCOUNTING";
    FixedDepositAccountingRequest request = new FixedDepositAccountingRequest(
        key, postingType,
        payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING
            ? payment.getDestinationAccountId() : payment.getSourceAccountId(),
        null, payment.getCurrencyCode(), payment.getBusinessDate(),
        OffsetDateTime.now(ZoneOffset.UTC), components,
        payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING
            ? payment.getSourceAccountId() : null,
        payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING
            ? null : payment.getDestinationAccountId(),
        null, null, payment.getReference());
    try {
      return support.attempt(payment, "ACCOUNTING_FD_POST", ACCOUNTING,
          () -> accounting.postFixedDeposit(request, key, payment.getCorrelationId()));
    } catch (PeerServiceException exception) {
      if (exception.getStatus() != 408 && exception.getStatus() != 504) throw exception;
      AccountingLookupResponse lookup = support.attempt(payment, "ACCOUNTING_FD_LOOKUP", ACCOUNTING,
          () -> accounting.findFixedDepositByReference(key, payment.getCorrelationId()));
      if (!"POSTED".equals(lookup.outcome()) || lookup.journalNumber() == null) throw exception;
      if (lookup.journal() != null) return lookup.journal();
      return new AccountingResponse(lookup.journalNumber(), null, key, "PAYMENTS-SERVICE",
          "FD_" + postingType, Instant.now(), payment.getBusinessDate(),
          payment.getCurrencyCode(), "POSTED", payment.getAmount(), payment.getAmount(),
          payment.getCorrelationId(), lookup.completedAt(), true, null);
    }
  }

  private void compensateBookTransfer(Payment payment, RuntimeException failure) {
    rememberFailure(payment, failure);
    boolean reversed = reverseAccountingIfNecessary(payment, failure.getMessage());
    releaseDepositIfNecessary(payment, "BOOK_TRANSFER_FAILED");
    PaymentStatus finalStatus = payment.getAccountingJournalNumber() == null
        ? PaymentStatus.FAILED : (reversed ? PaymentStatus.REVERSED : PaymentStatus.REVERSAL_PENDING);
    support.transition(payment, finalStatus, payment.getFailureCode(), payment.getFailureMessage());
    notifyFinal(payment, finalStatus == PaymentStatus.REVERSED ? "PAYMENT_REVERSED"
        : "PAYMENT_FAILED", payment.getFailureMessage());
  }

  private void compensateMerchant(Payment payment, RuntimeException failure) {
    rememberFailure(payment, failure);
    boolean reversed = reverseAccountingIfNecessary(payment, failure.getMessage());
    releaseCardIfNecessary(payment);
    PaymentStatus finalStatus = payment.getAccountingJournalNumber() == null
        ? PaymentStatus.FAILED : (reversed ? PaymentStatus.REVERSED : PaymentStatus.REVERSAL_PENDING);
    support.transition(payment, finalStatus, payment.getFailureCode(), payment.getFailureMessage());
    notifyFinal(payment, finalStatus == PaymentStatus.REVERSED ? "PAYMENT_REVERSED"
        : "PAYMENT_FAILED", payment.getFailureMessage());
  }

  private void compensateRepayment(Payment payment, RuntimeException failure,
                                   boolean depositCaptured) {
    rememberFailure(payment, failure);
    boolean reversed = reverseAccountingIfNecessary(payment, failure.getMessage());
    if (!depositCaptured) releaseDepositIfNecessary(payment, "CARD_REPAYMENT_FAILED");
    PaymentStatus finalStatus;
    if (payment.getAccountingJournalNumber() == null) finalStatus = PaymentStatus.FAILED;
    else if (depositCaptured) finalStatus = PaymentStatus.REVERSAL_PENDING;
    else finalStatus = reversed ? PaymentStatus.REVERSED : PaymentStatus.REVERSAL_PENDING;
    support.transition(payment, finalStatus, payment.getFailureCode(), payment.getFailureMessage());
    notifyFinal(payment, finalStatus == PaymentStatus.REVERSED ? "PAYMENT_REVERSED"
        : "PAYMENT_FAILED", payment.getFailureMessage());
  }

  private void compensateFixedDepositFunding(Payment payment, RuntimeException failure) {
    rememberFailure(payment, failure);
    boolean reversed = reverseAccountingIfNecessary(payment, failure.getMessage());
    releaseFixedDepositReservationIfNecessary(payment, "FD_FUNDING_FAILED");
    PaymentStatus finalStatus = payment.getAccountingJournalNumber() == null
        ? PaymentStatus.FAILED : (reversed ? PaymentStatus.REVERSED : PaymentStatus.REVERSAL_PENDING);
    support.transition(payment, finalStatus, payment.getFailureCode(), payment.getFailureMessage());
    notifyFinal(payment, finalStatus == PaymentStatus.REVERSED ? "PAYMENT_REVERSED"
        : "PAYMENT_FAILED", payment.getFailureMessage());
  }

  private void compensateFixedDepositPayout(Payment payment, RuntimeException failure) {
    rememberFailure(payment, failure);
    boolean reversed = reverseAccountingIfNecessary(payment, failure.getMessage());
    PaymentStatus finalStatus = payment.getAccountingJournalNumber() == null
        ? PaymentStatus.FAILED : (reversed ? PaymentStatus.REVERSED : PaymentStatus.REVERSAL_PENDING);
    support.transition(payment, finalStatus, payment.getFailureCode(), payment.getFailureMessage());
    notifyFinal(payment, finalStatus == PaymentStatus.REVERSED ? "PAYMENT_REVERSED"
        : "PAYMENT_FAILED", payment.getFailureMessage());
  }

  private boolean reverseAccountingIfNecessary(Payment payment, String reason) {
    if (payment.getAccountingJournalNumber() == null) return false;
    try {
      support.transition(payment, PaymentStatus.REVERSAL_PENDING,
          "DOWNSTREAM_SETTLEMENT_FAILED", reason);
      AccountingResponse reversal = support.attempt(payment, "ACCOUNTING_REVERSAL", ACCOUNTING,
          () -> accounting.reverse(payment.getAccountingJournalNumber(),
              new AccountingReversalRequest(payment.getPaymentId(), payment.getBusinessDate(),
                  Instant.now(), safe(reason)),
              "PAYMENT:" + payment.getPaymentId() + ":REVERSAL", payment.getCorrelationId()));
      payment.setReversalJournalNumber(reversal.journalNumber());
      payments.save(payment);
      return true;
    } catch (RuntimeException reversalFailure) {
      support.recordIgnoredFailure(payment, "ACCOUNTING_REVERSAL", ACCOUNTING, reversalFailure);
      return false;
    }
  }

  private void releaseDepositIfNecessary(Payment payment, String reasonCode) {
    if (payment.getDepositReservationId() == null) return;
    try {
      support.attempt(payment, "DEPOSIT_RELEASE", DEPOSIT,
          () -> deposit.release(payment.getDepositReservationId(), payment.getPaymentId(),
              reasonCode, payment.getCorrelationId()));
    } catch (RuntimeException releaseFailure) {
      support.recordIgnoredFailure(payment, "DEPOSIT_RELEASE", DEPOSIT, releaseFailure);
    }
  }

  private void releaseCardIfNecessary(Payment payment) {
    if (payment.getCardHoldId() == null) return;
    try {
      support.attempt(payment, "CARD_RELEASE", CARD,
          () -> cards.releaseHold(payment.getSourceAccountId(), payment.getCardHoldId(),
              payment.getCorrelationId()));
    } catch (RuntimeException releaseFailure) {
      support.recordIgnoredFailure(payment, "CARD_RELEASE", CARD, releaseFailure);
    }
  }

  private void releaseFixedDepositReservationIfNecessary(Payment payment, String reasonCode) {
    if (payment.getDepositReservationId() == null) return;
    try {
      support.attempt(payment, "FD_FUNDING_RELEASE", DEPOSIT,
          () -> deposit.releaseFixedDepositFunding(payment.getDepositReservationId(),
              payment.getPaymentId(), reasonCode, payment.getCorrelationId()));
    } catch (RuntimeException releaseFailure) {
      support.recordIgnoredFailure(payment, "FD_FUNDING_RELEASE", DEPOSIT, releaseFailure);
    }
  }

  private void notifyFinal(Payment payment, String type, String reason) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("paymentType", payment.getPaymentType().name().toLowerCase().replace('_', ' '));
    variables.put("amount", payment.getAmount().toPlainString());
    variables.put("currency", payment.getCurrencyCode());
    variables.put("transactionDate", payment.getBusinessDate().toString());
    if ("PAYMENT_SUCCESS".equals(type)) {
      variables.put("reference", payment.getPaymentId());
    } else if ("PAYMENT_FAILED".equals(type) && reason != null) {
      variables.put("failureReason", safe(reason));
    } else if ("PAYMENT_REVERSED".equals(type) && reason != null) {
      variables.put("reversalReason", safe(reason));
    }
    try {
      support.attempt(payment, "NOTIFICATION_SEND", "NOTIFICATION-SERVICE",
          () -> notifications.send(new NotificationRequest(payment.getRequestorCifId(), type,
              payment.getPaymentId(), variables),
              "payment-" + payment.getPaymentId() + "-"
                  + notificationFinalStatus(type).toLowerCase(),
              payment.getCorrelationId()));
    } catch (RuntimeException notificationFailure) {
      support.recordIgnoredFailure(payment, "NOTIFICATION_SEND", "NOTIFICATION-SERVICE",
          notificationFailure);
    }
  }

  private String notificationFinalStatus(String notificationType) {
    return switch (notificationType) {
      case "PAYMENT_SUCCESS" -> "SUCCESS";
      case "PAYMENT_FAILED" -> "FAILED";
      case "PAYMENT_REVERSED" -> "REVERSED";
      default -> throw new IllegalArgumentException(
          "Unsupported payment notification type: " + notificationType);
    };
  }

  private void validateBill(CardRepaymentRequest request, BillSummary bill) {
    if (!request.billId().equals(bill.billId()))
      throw new BusinessValidationException("Bill identifier does not match");
    if (!request.creditCardAccountId().equals(bill.accountId()))
      throw new BusinessValidationException("Bill belongs to a different card account");
    if (!"GENERATED".equals(bill.status()) && !"PARTIALLY_PAID".equals(bill.status())
        && !"OVERDUE".equals(bill.status()))
      throw new BusinessValidationException("Bill does not allow repayment");
    if (!request.currencyCode().equals(bill.currency()))
      throw new BusinessValidationException("Bill currency does not match payment currency");
    if (bill.outstandingAmount() == null || bill.outstandingAmount().signum() <= 0)
      throw new BusinessValidationException("Bill has no outstanding amount to repay");
    if (request.amount().compareTo(bill.outstandingAmount()) > 0)
      throw new BusinessValidationException("Repayment exceeds the bill outstanding amount");
  }

  private void validateFixedDepositPayout(FixedDepositPayoutRequest request) {
    if (request.paymentType() != PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT
        && request.paymentType() != PaymentType.FIXED_DEPOSIT_PREMATURE_PAYOUT) {
      throw new BusinessValidationException("Unsupported fixed-deposit payout paymentType");
    }
    if (request.destinationType() != InstrumentType.DEPOSIT_ACCOUNT) {
      throw new BusinessValidationException("Fixed-deposit payout destination must be DEPOSIT_ACCOUNT");
    }
    if (request.principalAmount().add(request.interestAmount())
        .compareTo(request.amount()) != 0) {
      throw new BusinessValidationException(
          "principalAmount plus interestAmount must equal the payout amount");
    }
  }

  private String fdPayoutType(PaymentType type) {
    return type == PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT ? "MATURITY" : "PREMATURE";
  }

  public PaymentResponse retryBillSettlement(String paymentId) {
    Payment payment = payments.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    if (payment.getStatus() == PaymentStatus.SETTLED) {
      return PaymentSupportService.response(payment);
    }
    if (payment.getStatus() != PaymentStatus.PENDING_BILLING
        || payment.getPaymentType() != PaymentType.CREDIT_CARD_REPAYMENT) {
      throw new BusinessValidationException(
          "Only a credit-card repayment in PENDING_BILLING can retry this callback");
    }
    try {
      recordBillSettlement(payment);
      payment.setFailureCode(null);
      payment.setFailureMessage(null);
      support.transition(payment, PaymentStatus.SETTLED, "BILLING_SETTLEMENT_RECORDED", null);
      notifyFinal(payment, "PAYMENT_SUCCESS", null);
    } catch (RuntimeException callbackFailure) {
      rememberFailure(payment, callbackFailure);
      support.transition(payment, PaymentStatus.PENDING_BILLING,
          payment.getFailureCode(), payment.getFailureMessage());
    }
    return PaymentSupportService.response(payment);
  }

  private void recordBillSettlement(Payment payment) {
    RuntimeException lastFailure = null;
    for (int attempt = 1; attempt <= 3; attempt++) {
      try {
        support.attempt(payment, "BILL_PAYMENT_SETTLEMENT", "BILL-GENERATION-SERVICE", () -> {
          billing.recordPaymentSettlement(payment.getBillId(),
              new BillPaymentSettlementRequest(payment.getPaymentId(),
                  payment.getAccountingJournalNumber(), payment.getAmount(),
                  payment.getCurrencyCode(), payment.getBillingSettlementAt()),
              payment.getCorrelationId());
          return null;
        });
        return;
      } catch (RuntimeException failure) {
        lastFailure = failure;
      }
    }
    throw lastFailure;
  }

  private Payment newPayment(Long cifId, String key, String fingerprint, PaymentType type,
      InstrumentType sourceType, String sourceId, InstrumentType destinationType,
      String destinationId, String merchantId, String billId, BigDecimal amount, String currency,
      String reference, String correlationId) {
    Payment payment = new Payment();
    payment.setPaymentId(PaymentSupportService.newPaymentId());
    payment.setRequestorCifId(cifId);
    payment.setIdempotencyKey(key);
    payment.setRequestFingerprint(fingerprint);
    payment.setPaymentType(type);
    payment.setSourceInstrumentType(sourceType);
    payment.setSourceAccountId(sourceId);
    payment.setDestinationInstrumentType(destinationType);
    payment.setDestinationAccountId(destinationId);
    payment.setMerchantId(merchantId);
    payment.setBillId(billId);
    payment.setAmount(amount);
    payment.setCurrencyCode(currency);
    payment.setStatus(PaymentStatus.PENDING_VALIDATION);
    payment.setReference(reference);
    payment.setCorrelationId(correlationId);
    payment.setBusinessDate(LocalDate.now(ZoneOffset.UTC));
    return payment;
  }

  private void rememberFailure(Payment payment, RuntimeException failure) {
    if (failure instanceof PeerServiceException peer) {
      payment.setFailureCode(peer.getCode());
      payment.setFailureMessage(safe(peer.getMessage()));
    } else if (failure instanceof BusinessValidationException validation) {
      payment.setFailureCode("VALIDATION_FAILED");
      payment.setFailureMessage(safe(String.join("; ", validation.getErrors())));
    } else {
      payment.setFailureCode("PAYMENT_PROCESSING_FAILED");
      payment.setFailureMessage(safe(failure.getMessage()));
    }
    payments.save(payment);
  }

  private String safe(String value) {
    if (value == null || value.isBlank()) return "Payment could not be completed";
    return value.length() <= 250 ? value : value.substring(0, 250);
  }
}
