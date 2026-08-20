package com.moneybags.payments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.dto.PaymentDtos.BookTransferRequest;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositPayoutRequest;
import com.moneybags.payments.dto.PaymentDtos.PaymentResponse;
import com.moneybags.payments.exception.PaymentCutoffException;
import com.moneybags.payments.repository.PaymentEodControlRepository;
import com.moneybags.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class EodControlServiceIntegrationTest {
  @Autowired EodControlService eod;
  @Autowired PaymentRepository payments;
  @Autowired PaymentEodControlRepository controls;
  @Autowired PaymentOrchestrationService orchestration;
  @Autowired PaymentQueryService queries;
  @Autowired TransactionTemplate transactions;

  @AfterEach
  void reopenIntake() {
    EodControlResponse state = eod.drain();
    if (!state.newPaymentIntake()) {
      String owner = state.commandReference();
      if (EodControlService.BOOTSTRAP_REFERENCE.equals(owner)) {
        owner = "TEST-RESET:" + UUID.randomUUID();
        eod.cutoff(state.businessDate(), state.currencyCode(), owner);
      }
      eod.reopen(state.businessDate(), state.businessDate(), state.currencyCode(),
          owner);
    }
  }

  @Test
  void cutoffSurvivesANewServiceInstanceAndReopenIsIdempotent() {
    LocalDate date = prepareOpenDate();
    String owner = "EOD:run-2041:PAYMENTS_CUTOFF";

    EodControlResponse cutoff = eod.cutoff(date, "INR", owner);
    assertThat(cutoff.newPaymentIntake()).isFalse();
    assertThat(cutoff.commandReference()).isEqualTo(owner);

    EodControlService restarted = new EodControlService(payments, controls, "INR");
    assertThatThrownBy(restarted::assertOpen).isInstanceOf(PaymentCutoffException.class);
    assertThat(eod.drain(date, "INR", owner).businessDate()).isEqualTo(date);
    assertThatThrownBy(() -> eod.drain(date, "INR", "EOD:other-run"))
        .isInstanceOf(PaymentCutoffException.class);

    EodControlResponse first = eod.reopen(date, "INR", owner);
    EodControlResponse replay = eod.reopen(date, "INR", owner);
    assertThat(first.status()).isEqualTo("OPEN");
    assertThat(replay.status()).isEqualTo("OPEN");
    assertThat(replay.newPaymentIntake()).isTrue();
    assertThat(replay.businessDate()).isEqualTo(date.plusDays(1));
    assertThatCode(new EodControlService(payments, controls, "INR")::assertOpen)
        .doesNotThrowAnyException();
    assertThat(eod.drain().status()).isEqualTo("OPEN");
  }

  @Test
  void sameDateCleanupCanAdoptAnOpenControlButCannotMoveANewerDateBackwards() {
    LocalDate date = prepareOpenDate();
    String priorOwner = "EOD:prior-run:PAYMENTS_BARRIER:EPOCH:1";
    String cleanupOwner = "EOD:cleanup-run:PAYMENTS_BARRIER:EPOCH:1";
    eod.cutoff(date, "INR", priorOwner);
    eod.reopen(date, date, "INR", priorOwner);

    EodControlResponse adopted = eod.reopen(date, date, "INR", cleanupOwner);
    assertThat(adopted.businessDate()).isEqualTo(date);
    assertThat(adopted.commandReference()).isEqualTo(cleanupOwner);

    eod.cutoff(date, "INR", cleanupOwner);
    EodControlResponse advanced = eod.reopen(date, date.plusDays(1), "INR", cleanupOwner);
    assertThat(advanced.businessDate()).isEqualTo(date.plusDays(1));

    assertThatThrownBy(() -> eod.reopen(date, date, "INR", "EOD:stale-run"))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("Stale EOD reopen");
    assertThat(eod.drain().businessDate()).isEqualTo(date.plusDays(1));
    assertThat(eod.drain().commandReference()).isEqualTo(cleanupOwner);
  }

  @Test
  void successfulReopenReplayMustMatchTheAdvancedDateCurrencyAndOwner() {
    LocalDate date = prepareOpenDate();
    String owner = "EOD:rollover-run:PAYMENTS_BARRIER:EPOCH:2";
    eod.cutoff(date, "INR", owner);
    eod.reopen(date, date.plusDays(1), "INR", owner);

    assertThat(eod.reopen(date, date.plusDays(1), "INR", owner).businessDate())
        .isEqualTo(date.plusDays(1));
    assertThatThrownBy(() -> eod.cutoff(date, "INR", "EOD:stale-cutoff"))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("cannot replace it");
    assertThatThrownBy(() -> eod.reopen(date, date.plusDays(1), "INR", "EOD:other-run"))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("does not match");
    assertThatThrownBy(() -> eod.reopen(date, date.plusDays(2), "INR", owner))
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("immediately following");
  }

  @Test
  void customerAndInternalPaymentsUseThePersistedControlDate() {
    LocalDate date = prepareOpenDate();
    String owner = "EOD:business-date-test:PAYMENTS_BARRIER:EPOCH:1";
    eod.cutoff(date, "INR", owner);
    eod.reopen(date, date, "INR", owner);

    PaymentResponse customer = orchestration.bookTransfer(new BookTransferRequest(
        91_001L, "date-source-" + UUID.randomUUID(), "date-target-" + UUID.randomUUID(),
        new BigDecimal("10.00"), "INR", "Persisted date test"),
        "date-key-" + UUID.randomUUID(), "date-trace-customer");
    assertThat(customer.businessDate()).isEqualTo(date);

    eod.cutoff(date, "INR", owner);
    PaymentResponse payout = orchestration.fixedDepositPayout(new FixedDepositPayoutRequest(
        PaymentType.FIXED_DEPOSIT_MATURITY_PAYOUT, 91_002L, "fd-date-source",
        InstrumentType.DEPOSIT_ACCOUNT, "date-payout-target", new BigDecimal("105.00"),
        new BigDecimal("100.00"), new BigDecimal("5.00"), "INR",
        "Persisted internal date test", "fd-date-test"),
        "date-payout-key-" + UUID.randomUUID(), "date-trace-payout");
    assertThat(payout.businessDate()).isEqualTo(date);
  }

  @Test
  void manualPendingReversalUsesThePersistedDateEvenDuringCutoff() {
    LocalDate date = prepareOpenDate();
    String owner = "EOD:reversal-date-test:PAYMENTS_BARRIER:EPOCH:1";
    Payment pending = payment(date.minusDays(3), "INR", PaymentType.BOOK_TRANSFER,
        PaymentStatus.REVERSAL_PENDING, "25.0000", true, null);
    payments.saveAndFlush(pending);
    eod.cutoff(date, "INR", owner);

    PaymentResponse reversed = queries.completePendingReversal(
        pending.getPaymentId(), "Manual recovery during EOD");

    assertThat(reversed.status()).isEqualTo(PaymentStatus.REVERSED);
    assertThat(payments.findById(pending.getPaymentId()).orElseThrow().getReversalBusinessDate())
        .isEqualTo(date);
  }

  @Test
  void cutoffWaitsForTheOuterPaymentTransactionThatHoldsTheControlLock() throws Exception {
    LocalDate date = prepareOpenDate();
    String setupOwner = "EOD:race-setup:PAYMENTS_BARRIER:EPOCH:1";
    String cutoffOwner = "EOD:race-cutoff:PAYMENTS_BARRIER:EPOCH:1";
    eod.cutoff(date, "INR", setupOwner);
    eod.reopen(date, date, "INR", setupOwner);

    CountDownLatch paymentHasLock = new CountDownLatch(1);
    CountDownLatch allowPaymentCommit = new CountDownLatch(1);
    CountDownLatch cutoffAttempted = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> paymentCommit = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
        LocalDate assignedDate = eod.acquireOpenBusinessDate();
        Payment inFlight = payment(assignedDate, "INR", PaymentType.BOOK_TRANSFER,
            PaymentStatus.PENDING_VALIDATION, "15.0000", false, null);
        payments.saveAndFlush(inFlight);
        paymentHasLock.countDown();
        try {
          if (!allowPaymentCommit.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to commit the payment transaction");
          }
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(exception);
        }
      }));
      assertThat(paymentHasLock.await(5, TimeUnit.SECONDS)).isTrue();

      Future<EodControlResponse> cutoff = executor.submit(() -> {
        cutoffAttempted.countDown();
        return eod.cutoff(date, "INR", cutoffOwner);
      });
      assertThat(cutoffAttempted.await(5, TimeUnit.SECONDS)).isTrue();
      TimeoutException blocked = catchThrowableOfType(
          () -> cutoff.get(300, TimeUnit.MILLISECONDS), TimeoutException.class);
      assertThat(blocked).isNotNull();

      allowPaymentCommit.countDown();
      paymentCommit.get(5, TimeUnit.SECONDS);
      assertThat(cutoff.get(5, TimeUnit.SECONDS).newPaymentIntake()).isFalse();
    } finally {
      allowPaymentCommit.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void publicCompatibilityReopenCannotBreakAnOwnedEodFence() {
    LocalDate date = prepareOpenDate();
    String owner = "EOD:run-owned:PAYMENTS_BARRIER:EPOCH:1";
    eod.cutoff(date, "INR", owner);

    assertThatThrownBy(eod::reopen)
        .isInstanceOf(PaymentCutoffException.class)
        .hasMessageContaining("owned by an active EOD run");
    assertThat(eod.drain(date, "INR", owner).newPaymentIntake()).isFalse();

    assertThat(eod.reopen(date, "INR", owner).newPaymentIntake()).isTrue();
  }

  @Test
  void drainReportsExactPaymentServiceOriginalAndReversalJournalsForDateAndCurrency() {
    LocalDate date = prepareOpenDate();
    Payment original = payment(date, "INR", PaymentType.BOOK_TRANSFER,
        PaymentStatus.SETTLED, "100.0000", true, null);
    Payment originalAndReversal = payment(date, "INR", PaymentType.CREDIT_CARD_REPAYMENT,
        PaymentStatus.REVERSED, "40.0000", true, date);
    Payment otherCurrency = payment(date, "USD", PaymentType.BOOK_TRANSFER,
        PaymentStatus.REVERSED, "900.0000", true, date);
    Payment otherDate = payment(date.plusDays(1), "INR", PaymentType.CREDIT_CARD_MERCHANT_PAYMENT,
        PaymentStatus.SETTLED, "700.0000", true, null);
    // Fixed-deposit originals belong to DEPOSIT-ACCOUNT-SERVICE. Their reversals are created by
    // Payments and therefore are correctly included in the PAYMENTS-SERVICE control total.
    Payment fixedDepositReversal = payment(date, "INR", PaymentType.FIXED_DEPOSIT_FUNDING,
        PaymentStatus.REVERSED, "30.0000", true, date);
    Payment pending = payment(date, "INR", PaymentType.BOOK_TRANSFER,
        PaymentStatus.PENDING_ACCOUNTING, "5.0000", false, null);
    Payment pendingOtherDate = payment(date.plusDays(1), "INR", PaymentType.BOOK_TRANSFER,
        PaymentStatus.PENDING_ACCOUNTING, "6.0000", false, null);
    payments.saveAllAndFlush(List.of(original, originalAndReversal, otherCurrency, otherDate,
        fixedDepositReversal, pending, pendingOtherDate));

    eod.cutoff(date, "INR");
    EodControlResponse draining = eod.drain();
    assertThat(draining.status()).isEqualTo("DRAINING");
    assertThat(draining.pendingPayments()).isEqualTo(1);
    assertThat(draining.currencyCode()).isEqualTo("INR");
    assertThat(draining.postedJournalCount()).isEqualTo(4);
    assertThat(draining.postedDebitTotal()).isEqualByComparingTo("210.0000");

    pending.setStatus(PaymentStatus.FAILED);
    payments.saveAndFlush(pending);
    EodControlResponse drained = eod.drain();
    assertThat(drained.status()).isEqualTo("DRAINED");
    assertThat(drained.pendingPayments()).isZero();
    assertThat(drained.postedJournalCount()).isEqualTo(4);
    assertThat(drained.postedDebitTotal()).isEqualByComparingTo("210.0000");
  }

  private static Payment payment(LocalDate date, String currency, PaymentType type,
                                 PaymentStatus status, String amount,
                                 boolean originalJournal, LocalDate reversalDate) {
    String id = UUID.randomUUID().toString();
    Payment payment = new Payment();
    payment.setPaymentId(id);
    payment.setRequestorCifId(Math.abs((long) id.hashCode()) + 10_000L);
    payment.setIdempotencyKey("eod-test-" + id);
    payment.setRequestFingerprint("a".repeat(64));
    payment.setPaymentType(type);
    payment.setSourceInstrumentType(InstrumentType.DEPOSIT_ACCOUNT);
    payment.setSourceAccountId("source-" + id);
    payment.setDestinationInstrumentType(InstrumentType.DEPOSIT_ACCOUNT);
    payment.setDestinationAccountId("destination-" + id);
    payment.setAmount(new BigDecimal(amount));
    payment.setCurrencyCode(currency);
    payment.setStatus(status);
    payment.setCorrelationId("correlation-" + id);
    payment.setBusinessDate(date);
    if (originalJournal) payment.setAccountingJournalNumber("JRN-ORIGINAL-" + id);
    if (reversalDate != null) {
      payment.setReversalJournalNumber("JRN-REVERSAL-" + id);
      payment.setReversalBusinessDate(reversalDate);
    }
    return payment;
  }

  private LocalDate prepareOpenDate() {
    EodControlResponse state = eod.drain();
    String setupOwner = "TEST-SETUP:" + UUID.randomUUID();
    if (!state.newPaymentIntake()) {
      String owner = state.commandReference();
      if (EodControlService.BOOTSTRAP_REFERENCE.equals(owner)) {
        owner = setupOwner;
        eod.cutoff(state.businessDate(), state.currencyCode(), owner);
      }
      eod.reopen(state.businessDate(), state.businessDate(), state.currencyCode(), owner);
      state = eod.drain();
    }
    LocalDate target = state.businessDate().plusDays(7);
    eod.reopen(target, target, state.currencyCode(), setupOwner);
    return target;
  }
}
