package com.moneybags.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.dto.PaymentDtos.*;
import com.moneybags.payments.exception.IdempotencyConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentOrchestrationIntegrationTest {
  @Autowired PaymentOrchestrationService orchestration;
  @Autowired PaymentQueryService queries;
  @Autowired EodControlService eod;

  @BeforeEach
  void establishAnOpenTestBusinessDate() {
    EodControlResponse state = eod.drain();
    if (state.newPaymentIntake()) return;
    String owner = state.commandReference();
    if (EodControlService.BOOTSTRAP_REFERENCE.equals(owner)) {
      owner = "TEST-ORCHESTRATION:" + UUID.randomUUID();
      eod.cutoff(state.businessDate(), state.currencyCode(), owner);
    }
    eod.reopen(state.businessDate(), state.businessDate(), state.currencyCode(), owner);
  }

  @Test
  void settlesBookTransferAndReturnsSamePaymentForReplay() {
    BookTransferRequest request = new BookTransferRequest(101L, "dep-acc-001",
        "dep-acc-002", new BigDecimal("500.00"), "INR", "Rent payment");
    PaymentResponse created = orchestration.bookTransfer(request, "book-key-1", "trace-1");
    PaymentResponse replayed = orchestration.bookTransfer(request, "book-key-1", "trace-2");
    assertThat(created.status()).isEqualTo(PaymentStatus.SETTLED);
    assertThat(replayed.paymentId()).isEqualTo(created.paymentId());
    assertThat(created.depositReservationId()).isNotBlank();
    assertThat(created.accountingJournalNumber()).isNotBlank();
  }

  @Test
  void rejectsChangedRequestWithSameIdempotencyKey() {
    orchestration.bookTransfer(new BookTransferRequest(102L, "dep-a", "dep-b",
        new BigDecimal("50.00"), "INR", null), "book-key-2", "trace-2");
    assertThatThrownBy(() -> orchestration.bookTransfer(new BookTransferRequest(102L,
        "dep-a", "dep-b", new BigDecimal("60.00"), "INR", null),
        "book-key-2", "trace-2")).isInstanceOf(IdempotencyConflictException.class);
  }

  @Test
  void settlesMerchantPaymentUsingHoldAndCapture() {
    MerchantPaymentRequest request = new MerchantPaymentRequest(
        103L, "101", "MERCHANT-001", new BigDecimal("1000.00"), "INR",
        "Shop purchase");
    PaymentResponse result = orchestration.merchantPayment(request, "merchant-key-1", "trace-3");
    PaymentResponse replay = orchestration.merchantPayment(new MerchantPaymentRequest(
        103L, "CC-101", "MERCHANT-001", new BigDecimal("1000.00"), "INR",
        "Shop purchase"), "merchant-key-1", "trace-3b");
    assertThat(result.status()).describedAs("failure=%s: %s", result.failureCode(),
        result.failureMessage()).isEqualTo(PaymentStatus.SETTLED);
    assertThat(result.cardHoldId()).isNotBlank();
    assertThat(result.sourceAccountId()).isEqualTo("CC-101");
    assertThat(replay.paymentId()).isEqualTo(result.paymentId());
  }

  @Test
  void settlesBillRepaymentAndReturnsStatementActivity() {
    PaymentResponse result = orchestration.cardRepayment(new CardRepaymentRequest(
        104L, "BILL-202608-001", "dep-statement", "101",
        new BigDecimal("2500.00"), "INR", "Card bill repayment"),
        "repayment-key-1", "trace-4");
    assertThat(result.status()).isEqualTo(PaymentStatus.SETTLED);
    assertThat(result.destinationAccountId()).isEqualTo("CC-101");
    PageResponse<StatementActivity> statement = queries.statements("dep-statement",
        LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), 0, 100);
    assertThat(statement.content()).extracting(StatementActivity::paymentId)
        .contains(result.paymentId());
    assertThat(statement.content()).extracting(StatementActivity::direction).contains("DEBIT");
  }

  @Test
  void rejectsRepaymentAboveBillOutstandingAmount() {
    PaymentResponse result = orchestration.cardRepayment(new CardRepaymentRequest(
        106L, "BILL-202608-OVERPAY", "dep-overpay", "101",
        new BigDecimal("50000.01"), "INR", "Too large repayment"),
        "repayment-key-overpay", "trace-6");
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(result.failureMessage()).contains("outstanding amount");
    assertThat(result.depositReservationId()).isNull();
  }

  @Test
  void keepsFinanciallyCompletedRepaymentPendingWhenBillingCallbackFails() {
    PaymentResponse result = orchestration.cardRepayment(new CardRepaymentRequest(
        107L, "BILL-CALLBACK-FAIL", "dep-callback", "101",
        new BigDecimal("1000.00"), "INR", "Callback recovery test"),
        "repayment-key-callback", "trace-7");
    assertThat(result.status()).isEqualTo(PaymentStatus.PENDING_BILLING);
    assertThat(result.accountingJournalNumber()).isNotBlank();
  }

  @Test
  void recordsBusinessFailureForInsufficientCardLimit() {
    PaymentResponse result = orchestration.merchantPayment(new MerchantPaymentRequest(
        105L, "101", "MERCHANT-002", new BigDecimal("150000.00"), "INR",
        "Large purchase"), "merchant-key-2", "trace-5");
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(result.failureCode()).isEqualTo("INSUFFICIENT_LIMIT");
  }

  @Test
  void fundsAndActivatesFixedDeposit() {
    PaymentResponse result = orchestration.fixedDepositFunding(
        new FixedDepositFundingRequest(108L, "dep-fd-source", "fd-001",
            new BigDecimal("100000.00"), "INR", "Initial FD funding"),
        "fd-funding-key-1", "trace-8");
    assertThat(result.status()).isEqualTo(PaymentStatus.SETTLED);
    assertThat(result.paymentType()).isEqualTo(PaymentType.FIXED_DEPOSIT_FUNDING);
    assertThat(result.fixedDepositId()).isEqualTo("fd-001");
    assertThat(result.depositReservationId()).isNotBlank();
    assertThat(result.accountingJournalNumber()).isNotBlank();
  }

  @Test
  void settlesFixedDepositMaturityPayout() {
    PaymentResponse result = orchestration.fixedDepositPayout(
        new FixedDepositPayoutRequest(PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT,
            109L, "fd-account-002", InstrumentType.DEPOSIT_ACCOUNT, "dep-payout-001",
            new BigDecimal("106968.00"), new BigDecimal("100000.00"),
            new BigDecimal("6968.00"), "INR", "FD maturity payout", "fd-002"),
        "fd-payout-key-1", "trace-9");
    assertThat(result.status()).isEqualTo(PaymentStatus.SETTLED);
    assertThat(result.paymentType()).isEqualTo(PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT);
    assertThat(result.principalAmount()).isEqualByComparingTo("100000.00");
    assertThat(result.interestAmount()).isEqualByComparingTo("6968.00");
  }

  @Test
  void rejectsFixedDepositPayoutWithInvalidBreakdown() {
    PaymentResponse result = orchestration.fixedDepositPayout(
        new FixedDepositPayoutRequest(PaymentType.FIXED_DEPOSIT_PREMATURE_PAYOUT,
            110L, "fd-account-003", InstrumentType.DEPOSIT_ACCOUNT, "dep-payout-002",
            new BigDecimal("90000.00"), new BigDecimal("85000.00"),
            new BigDecimal("4000.00"), "INR", "FD premature payout", "fd-003"),
        "fd-payout-key-2", "trace-10");
    assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
    assertThat(result.failureMessage()).contains("must equal the payout amount");
    assertThat(result.accountingJournalNumber()).isNull();
  }
}
