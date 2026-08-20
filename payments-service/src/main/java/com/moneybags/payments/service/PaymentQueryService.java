package com.moneybags.payments.service;

import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentStatusHistory;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.dto.IntegrationDtos.AccountingResponse;
import com.moneybags.payments.dto.IntegrationDtos.AccountingReversalRequest;
import com.moneybags.payments.dto.IntegrationDtos.NotificationRequest;
import com.moneybags.payments.dto.PaymentDtos.*;
import com.moneybags.payments.exception.BusinessValidationException;
import com.moneybags.payments.exception.ResourceNotFoundException;
import com.moneybags.payments.integration.AccountingClient;
import com.moneybags.payments.integration.CreditCardClient;
import com.moneybags.payments.integration.DepositAccountClient;
import com.moneybags.payments.integration.NotificationClient;
import com.moneybags.payments.repository.PaymentRepository;
import com.moneybags.payments.repository.PaymentStatusHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentQueryService {
  private final PaymentRepository payments;
  private final PaymentStatusHistoryRepository history;
  private final PaymentSupportService support;
  private final EodControlService eod;
  private final DepositAccountClient deposit;
  private final CreditCardClient cards;
  private final AccountingClient accounting;
  private final NotificationClient notifications;

  public PaymentQueryService(PaymentRepository payments, PaymentStatusHistoryRepository history,
      PaymentSupportService support, EodControlService eod,
      DepositAccountClient deposit, CreditCardClient cards,
      AccountingClient accounting, NotificationClient notifications) {
    this.payments = payments;
    this.history = history;
    this.support = support;
    this.eod = eod;
    this.deposit = deposit;
    this.cards = cards;
    this.accounting = accounting;
    this.notifications = notifications;
  }

  @Transactional(readOnly = true)
  public PaymentResponse get(String paymentId) {
    return PaymentSupportService.response(require(paymentId));
  }

  @Transactional(readOnly = true)
  public PageResponse<PaymentResponse> byCustomer(Long cifId, int page, int size) {
    Page<Payment> result = payments.findByRequestorCifIdOrderByCreatedAtDesc(cifId,
        PageRequest.of(page, size));
    return page(result.map(PaymentSupportService::response));
  }

  @Transactional(readOnly = true)
  public List<PaymentStatusHistoryResponse> history(String paymentId) {
    require(paymentId);
    return history.findByPaymentIdOrderByChangedAtAsc(paymentId).stream()
        .map(row -> new PaymentStatusHistoryResponse(row.getFromStatus(), row.getToStatus(),
            row.getReasonCode(), row.getReasonMessage(), row.getCorrelationId(),
            row.getChangedAt()))
        .toList();
  }

  public PaymentResponse cancel(String paymentId) {
    Payment payment = require(paymentId);
    if (payment.getStatus() == PaymentStatus.CANCELLED) return PaymentSupportService.response(payment);
    if (payment.getStatus().isFinal() || payment.getAccountingJournalNumber() != null) {
      throw new BusinessValidationException("Only an unsettled payment can be cancelled");
    }
    if (payment.getDepositReservationId() != null) {
      if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING) {
        support.attempt(payment, "FD_FUNDING_RELEASE", "DEPOSIT-ACCOUNT-SERVICE",
            () -> deposit.releaseFixedDepositFunding(payment.getDepositReservationId(), paymentId,
                "PAYMENT_CANCELLED", payment.getCorrelationId()));
      } else {
        support.attempt(payment, "DEPOSIT_RELEASE", "DEPOSIT-ACCOUNT-SERVICE",
            () -> deposit.release(payment.getDepositReservationId(), paymentId,
                "PAYMENT_CANCELLED", payment.getCorrelationId()));
      }
    }
    if (payment.getCardHoldId() != null) {
      support.attempt(payment, "CARD_RELEASE", "CREDIT-CARD-SERVICE",
          () -> cards.releaseHold(payment.getSourceAccountId(), payment.getCardHoldId(),
              payment.getCorrelationId()));
    }
    support.transition(payment, PaymentStatus.CANCELLED, "PAYMENT_CANCELLED", null);
    return PaymentSupportService.response(payment);
  }

  public PaymentResponse completePendingReversal(String paymentId, String reason) {
    LocalDate reversalBusinessDate = eod.acquireBusinessDate();
    Payment payment = require(paymentId);
    if (payment.getStatus() == PaymentStatus.REVERSED) return PaymentSupportService.response(payment);
    if (payment.getStatus() != PaymentStatus.REVERSAL_PENDING
        || payment.getAccountingJournalNumber() == null) {
      throw new BusinessValidationException(
          "Only a REVERSAL_PENDING payment with a journal can be reversed here");
    }
    AccountingResponse response = support.attempt(payment, "ACCOUNTING_REVERSAL",
        "ACCOUNTING-SERVICE", () -> accounting.reverse(payment.getAccountingJournalNumber(),
            new AccountingReversalRequest(paymentId, reversalBusinessDate, Instant.now(),
                reason), "PAYMENT:" + paymentId + ":REVERSAL", payment.getCorrelationId()));
    payment.setReversalJournalNumber(response.journalNumber());
    payment.setReversalBusinessDate(response.businessDate());
    support.transition(payment, PaymentStatus.REVERSED, "ACCOUNTING_REVERSED", reason);
    notifyReversed(payment, reason);
    return PaymentSupportService.response(payment);
  }

  private void notifyReversed(Payment payment, String reason) {
    Map<String, String> variables = new LinkedHashMap<>();
    variables.put("paymentType",
        payment.getPaymentType().name().toLowerCase().replace('_', ' '));
    variables.put("amount", payment.getAmount().toPlainString());
    variables.put("currency", payment.getCurrencyCode());
    variables.put("transactionDate", payment.getBusinessDate().toString());
    variables.put("reversalReason", reason);
    try {
      support.attempt(payment, "NOTIFICATION_SEND", "NOTIFICATION-SERVICE", () ->
          notifications.send(new NotificationRequest(payment.getRequestorCifId(),
                  "PAYMENT_REVERSED", payment.getPaymentId(), variables),
              "payment-" + payment.getPaymentId() + "-reversed",
              payment.getCorrelationId()));
    } catch (RuntimeException notificationFailure) {
      support.recordIgnoredFailure(payment, "NOTIFICATION_SEND", "NOTIFICATION-SERVICE",
          notificationFailure);
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<PaymentResponse> internal(PaymentStatus status, LocalDate businessDate,
                                                int page, int size) {
    LocalDate date = businessDate == null ? LocalDate.now(ZoneOffset.UTC) : businessDate;
    Page<Payment> result = status == null
        ? payments.findByBusinessDateOrderByCreatedAtAsc(date, PageRequest.of(page, size))
        : payments.findByStatusAndBusinessDateOrderByCreatedAtAsc(status, date,
            PageRequest.of(page, size));
    return page(result.map(PaymentSupportService::response));
  }

  @Transactional(readOnly = true)
  public PageResponse<StatementActivity> statements(String accountId, LocalDate from,
      LocalDate to, int pageNumber, int pageSize) {
    Instant start = from.atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    List<StatementActivity> all = new ArrayList<>();
    for (Payment payment : payments.findStatementPayments(accountId, start, end)) {
      List<PaymentStatusHistory> rows = history.findByPaymentIdOrderByChangedAtAsc(
          payment.getPaymentId());
      rows.stream().filter(row -> row.getToStatus() == PaymentStatus.SETTLED)
          .findFirst().ifPresent(row -> all.add(activity(payment, accountId,
              "PAYMENT_SETTLED", row.getChangedAt(), false)));
      rows.stream().filter(row -> row.getToStatus() == PaymentStatus.REVERSED)
          .findFirst().ifPresent(row -> all.add(activity(payment, accountId,
              "PAYMENT_REVERSED", row.getChangedAt(), true)));
    }
    all.sort(Comparator.comparing(StatementActivity::occurredAt));
    int fromIndex = Math.min(pageNumber * pageSize, all.size());
    int toIndex = Math.min(fromIndex + pageSize, all.size());
    int totalPages = all.isEmpty() ? 0 : (int) Math.ceil(all.size() / (double) pageSize);
    return new PageResponse<>(List.copyOf(all.subList(fromIndex, toIndex)), pageNumber, pageSize,
        all.size(), totalPages);
  }

  private StatementActivity activity(Payment payment, String accountId, String activityType,
                                     Instant occurredAt, boolean reversal) {
    boolean isSource = accountId.equals(payment.getSourceAccountId());
    String normalDirection = isSource ? "DEBIT" : "CREDIT";
    String direction = reversal ? invert(normalDirection) : normalDirection;
    String counterparty = isSource ? payment.getDestinationAccountId() : payment.getSourceAccountId();
    String description = reversal ? "Reversal of " + description(payment) : description(payment);
    return new StatementActivity(payment.getPaymentId(), activityType, payment.getPaymentType(),
        accountId, direction, counterparty, payment.getAmount(), payment.getCurrencyCode(),
        description, occurredAt, reversal ? payment.getPaymentId() : null);
  }

  private String description(Payment payment) {
    if (payment.getReference() != null) return payment.getReference();
    if (payment.getPaymentType() == PaymentType.BOOK_TRANSFER) return "Book transfer";
    if (payment.getPaymentType() == PaymentType.CREDIT_CARD_REPAYMENT)
      return "Credit-card bill repayment";
    if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING)
      return "Fixed-deposit funding";
    if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT)
      return "Fixed-deposit maturity payout";
    if (payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_PREMATURE_PAYOUT)
      return "Fixed-deposit premature-closure payout";
    return "Credit-card merchant payment";
  }

  private String invert(String direction) {
    return "DEBIT".equals(direction) ? "CREDIT" : "DEBIT";
  }

  private Payment require(String id) {
    return payments.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment", id));
  }

  private <T> PageResponse<T> page(Page<T> result) {
    return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
        result.getTotalElements(), result.getTotalPages());
  }
}
