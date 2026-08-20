package com.moneybags.payments.service;

import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.domain.PaymentEodControl;
import com.moneybags.payments.dto.PaymentDtos.EodControlResponse;
import com.moneybags.payments.exception.PaymentCutoffException;
import com.moneybags.payments.repository.PaymentEodControlRepository;
import com.moneybags.payments.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EodControlService {
  private static final String CONTROL_ID = "PAYMENTS";
  static final String BOOTSTRAP_REFERENCE = "SYSTEM:BOOTSTRAP:CLOSED";
  private static final List<PaymentStatus> PENDING = List.of(
      PaymentStatus.PENDING_VALIDATION, PaymentStatus.PENDING_RESERVATION,
      PaymentStatus.PENDING_ACCOUNTING, PaymentStatus.PENDING_SETTLEMENT,
      PaymentStatus.PENDING_BILLING, PaymentStatus.REVERSAL_PENDING);
  private static final List<PaymentType> PAYMENT_SERVICE_JOURNAL_TYPES = List.of(
      PaymentType.BOOK_TRANSFER, PaymentType.CREDIT_CARD_MERCHANT_PAYMENT,
      PaymentType.CREDIT_CARD_REPAYMENT);

  private final PaymentRepository payments;
  private final PaymentEodControlRepository controls;
  private final String defaultCurrency;

  public EodControlService(PaymentRepository payments,
                           PaymentEodControlRepository controls,
                           @Value("${moneybags.payments.eod.default-currency:INR}") String defaultCurrency) {
    this.payments = payments;
    this.controls = controls;
    this.defaultCurrency = normalizeCurrency(defaultCurrency);
  }

  @Transactional(readOnly = true)
  public void assertOpen() {
    if (!state().intakeOpen()) {
      throw new PaymentCutoffException("New payment intake is closed for EOD");
    }
  }

  /**
   * Acquires the singleton control row for the caller's transaction. Payment orchestration calls
   * this from its outer transaction and therefore holds the lock until the payment commits.
   */
  @Transactional
  public LocalDate acquireOpenBusinessDate() {
    PaymentEodControl control = lockedState();
    if (!control.intakeOpen()) {
      throw new PaymentCutoffException("New payment intake is closed for EOD");
    }
    return requireBusinessDate(control);
  }

  /** Internal financial operations use the persisted date even while customer intake is cut off. */
  @Transactional
  public LocalDate acquireBusinessDate() {
    PaymentEodControl control = lockedState();
    if (isBootstrapClosed(control)) {
      throw new PaymentCutoffException(
          "Payments business date has not yet been established by EOD");
    }
    return requireBusinessDate(control);
  }

  @Transactional
  public EodControlResponse cutoff(LocalDate businessDate, String requestedCurrency,
                                   String commandReference) {
    String currency = requestedCurrency == null || requestedCurrency.isBlank()
        ? defaultCurrency : normalizeCurrency(requestedCurrency);
    String reference = requireReference(commandReference);
    PaymentEodControl control = lockedState();
    if (isBootstrapClosed(control)) {
      control.cutoff(Objects.requireNonNull(businessDate, "businessDate"), currency, reference);
      return response(control, "CUT_OFF");
    }
    if (control.intakeOpen()
        && (!Objects.equals(control.businessDate(), businessDate)
            || !Objects.equals(control.currencyCode(), currency))) {
      throw new PaymentCutoffException("Payment intake is open for "
          + control.businessDate() + " " + control.currencyCode()
          + "; a cutoff cannot replace it with a different business date or currency");
    }
    if (!control.intakeOpen()
        && (!Objects.equals(control.businessDate(), businessDate)
            || !Objects.equals(control.currencyCode(), currency)
            || (control.commandReference() != null
                && !Objects.equals(control.commandReference(), reference)))) {
      throw new PaymentCutoffException("Payment intake is already closed for "
          + control.businessDate() + " " + control.currencyCode());
    }
    if (control.intakeOpen() || control.commandReference() == null) {
      control.cutoff(businessDate, currency, reference);
    }
    return response(control, "CUT_OFF");
  }

  @Transactional
  public EodControlResponse cutoff(LocalDate businessDate, String requestedCurrency) {
    return cutoff(businessDate, requestedCurrency, "LEGACY-EOD:" + businessDate);
  }

  /** Compatibility overload used by callers that predate the additive currency contract. */
  @Transactional
  public EodControlResponse cutoff(LocalDate businessDate) {
    return cutoff(businessDate, defaultCurrency, "LEGACY-EOD:" + businessDate);
  }

  @Transactional(readOnly = true)
  public EodControlResponse drain() {
    PaymentEodControl control = state();
    long pending = pending(control);
    String status = control.intakeOpen() ? "OPEN" : pending == 0 ? "DRAINED" : "DRAINING";
    return response(control, status);
  }

  @Transactional
  public EodControlResponse drain(LocalDate businessDate, String requestedCurrency,
                                  String commandReference) {
    PaymentEodControl control = lockedState();
    assertOwned(control, businessDate, requestedCurrency, commandReference);
    long pending = pending(control);
    return response(control, pending == 0 ? "DRAINED" : "DRAINING");
  }

  @Transactional
  public EodControlResponse reopen() {
    PaymentEodControl control = lockedState();
    if (!control.intakeOpen()) {
      if (control.commandReference() != null && !control.commandReference().isBlank()) {
        throw new PaymentCutoffException("Payment intake is owned by an active EOD run; resume that run to reopen");
      }
      control.reopen();
    }
    return response(control, "OPEN");
  }

  @Transactional
  public EodControlResponse reopen(LocalDate businessDate, String requestedCurrency,
                                   String commandReference) {
    return reopen(businessDate, businessDate.plusDays(1), requestedCurrency, commandReference);
  }

  @Transactional
  public EodControlResponse reopen(LocalDate businessDate, LocalDate nextBusinessDate,
                                   String requestedCurrency, String commandReference) {
    Objects.requireNonNull(businessDate, "businessDate");
    Objects.requireNonNull(nextBusinessDate, "nextBusinessDate");
    if (!nextBusinessDate.equals(businessDate)
        && !nextBusinessDate.equals(businessDate.plusDays(1))) {
      throw new PaymentCutoffException(
          "Payments may reopen only on the EOD date or its immediately following business date");
    }
    String currency = requestedCurrency == null || requestedCurrency.isBlank()
        ? defaultCurrency : normalizeCurrency(requestedCurrency);
    String reference = requireReference(commandReference);
    PaymentEodControl control = lockedState();
    if (!control.intakeOpen()) {
      if (isBootstrapClosed(control)) {
        throw new PaymentCutoffException(
            "Payments bootstrap fence must be owned by an EOD cutoff before reopening");
      }
      assertOwned(control, businessDate, currency, reference);
      control.reopen(nextBusinessDate, currency, reference);
    } else if (nextBusinessDate.equals(businessDate)) {
      // Before any mutating EOD step, cleanup may find a freshly initialized/open row or an
      // already-open row carrying the previous command reference. Adoption is safe only on the
      // exact same business date; it must never move a newer control backwards.
      if (control.businessDate() != null && control.businessDate().isAfter(nextBusinessDate)) {
        throw new PaymentCutoffException(
            "Stale EOD reopen cannot overwrite a newer Payments business date");
      }
      control.acknowledgeOpen(nextBusinessDate, currency, reference);
    } else {
      assertOpenReplay(control, nextBusinessDate, currency, reference);
    }
    return response(control, "OPEN");
  }

  private EodControlResponse response(PaymentEodControl control, String status) {
    JournalTotals totals = journalTotals(control);
    return new EodControlResponse(control.businessDate(), status, control.intakeOpen(),
        pending(control), control.currencyCode(), totals.count(), totals.debit(),
        control.commandReference());
  }

  private void assertOwned(PaymentEodControl control, LocalDate businessDate,
                           String requestedCurrency, String commandReference) {
    String currency = requestedCurrency == null || requestedCurrency.isBlank()
        ? defaultCurrency : normalizeCurrency(requestedCurrency);
    String reference = requireReference(commandReference);
    if (control.intakeOpen()) {
      throw new PaymentCutoffException("Payment intake is open; EOD cutoff ownership was lost");
    }
    if (!Objects.equals(control.businessDate(), businessDate)
        || !Objects.equals(control.currencyCode(), currency)
        || !Objects.equals(control.commandReference(), reference)) {
      throw new PaymentCutoffException("Payment EOD cutoff is owned by a different command");
    }
  }

  private void assertOpenReplay(PaymentEodControl control, LocalDate nextBusinessDate,
                                String currency, String reference) {
    if (!Objects.equals(control.businessDate(), nextBusinessDate)
        || !Objects.equals(control.currencyCode(), currency)
        || !Objects.equals(control.commandReference(), reference)) {
      throw new PaymentCutoffException(
          "EOD reopen replay does not match the current Payments business date and owner");
    }
  }

  private LocalDate requireBusinessDate(PaymentEodControl control) {
    if (control.businessDate() == null) {
      throw new IllegalStateException("Payments EOD control business date is not initialized");
    }
    return control.businessDate();
  }

  private boolean isBootstrapClosed(PaymentEodControl control) {
    return !control.intakeOpen() && BOOTSTRAP_REFERENCE.equals(control.commandReference());
  }

  private long pending(PaymentEodControl control) {
    if (control.businessDate() == null || control.currencyCode() == null) {
      return payments.countByStatusIn(PENDING);
    }
    return payments.countByStatusInAndBusinessDateAndCurrencyCode(
        PENDING, control.businessDate(), control.currencyCode());
  }

  private JournalTotals journalTotals(PaymentEodControl control) {
    if (control.businessDate() == null || control.currencyCode() == null) {
      return new JournalTotals(0, BigDecimal.ZERO.setScale(4));
    }
    long originalCount = payments.countPaymentServiceJournals(
        control.businessDate(), control.currencyCode(), PAYMENT_SERVICE_JOURNAL_TYPES);
    long reversalCount = payments.countPaymentServiceReversalJournals(
        control.businessDate(), control.currencyCode());
    BigDecimal originalDebit = zero(payments.totalPaymentServiceJournalDebits(
        control.businessDate(), control.currencyCode(), PAYMENT_SERVICE_JOURNAL_TYPES));
    BigDecimal reversalDebit = zero(payments.totalPaymentServiceReversalDebits(
        control.businessDate(), control.currencyCode()));
    return new JournalTotals(originalCount + reversalCount,
        originalDebit.add(reversalDebit).setScale(4));
  }

  private PaymentEodControl state() {
    return controls.findById(CONTROL_ID).orElseThrow(() ->
        new IllegalStateException("Payments EOD control row is missing"));
  }

  private PaymentEodControl lockedState() {
    return controls.findByIdForUpdate(CONTROL_ID).orElseThrow(() ->
        new IllegalStateException("Payments EOD control row is missing"));
  }

  private static String normalizeCurrency(String value) {
    String currency = Objects.requireNonNull(value, "currency").trim().toUpperCase(Locale.ROOT);
    if (!currency.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException("Payments EOD currency must be a three-letter ISO code");
    }
    return currency;
  }

  private static String requireReference(String value) {
    String reference = Objects.requireNonNull(value, "commandReference").trim();
    if (reference.isEmpty() || reference.length() > 100) {
      throw new IllegalArgumentException("Payments EOD command reference must contain 1..100 characters");
    }
    return reference;
  }

  private static BigDecimal zero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4);
  }

  private record JournalTotals(long count, BigDecimal debit) { }
}
