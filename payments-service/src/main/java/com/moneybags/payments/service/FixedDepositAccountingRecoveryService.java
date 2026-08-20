package com.moneybags.payments.service;

import com.moneybags.payments.domain.FixedDepositAccountingRecovery;
import com.moneybags.payments.domain.InstrumentType;
import com.moneybags.payments.domain.Payment;
import com.moneybags.payments.domain.PaymentAttempt;
import com.moneybags.payments.domain.PaymentStatus;
import com.moneybags.payments.domain.PaymentStatusHistory;
import com.moneybags.payments.domain.PaymentType;
import com.moneybags.payments.dto.IntegrationDtos.AccountingAccountClearanceResponse;
import com.moneybags.payments.dto.IntegrationDtos.AccountingLookupResponse;
import com.moneybags.payments.dto.IntegrationDtos.AccountingResponse;
import com.moneybags.payments.dto.IntegrationDtos.FixedDepositAccountingComponent;
import com.moneybags.payments.dto.IntegrationDtos.FixedDepositAccountingRequest;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryCandidate;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryPreview;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryRequest;
import com.moneybags.payments.dto.PaymentDtos.FixedDepositAccountingRecoveryResponse;
import com.moneybags.payments.dto.PaymentDtos.PageResponse;
import com.moneybags.payments.exception.BusinessValidationException;
import com.moneybags.payments.exception.PeerServiceException;
import com.moneybags.payments.exception.ResourceNotFoundException;
import com.moneybags.payments.integration.AccountingClient;
import com.moneybags.payments.repository.FixedDepositAccountingRecoveryRepository;
import com.moneybags.payments.repository.PaymentAttemptRepository;
import com.moneybags.payments.repository.PaymentRepository;
import com.moneybags.payments.repository.PaymentStatusHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Explicit, operator-confirmed recovery of principal-funding journals for legacy fixed deposits.
 * This is intentionally not called by EOD or application startup.
 */
@Service
public class FixedDepositAccountingRecoveryService {
  public static final String CONFIRMATION = "REPLAY_AUTHORITATIVE_FD_FUNDING";
  private static final String ACCOUNTING = "ACCOUNTING-SERVICE";
  private static final String SOURCE_SERVICE = "DEPOSIT-ACCOUNT-SERVICE";

  private final PaymentRepository payments;
  private final PaymentAttemptRepository attempts;
  private final PaymentStatusHistoryRepository history;
  private final FixedDepositAccountingRecoveryRepository recoveries;
  private final FixedDepositAccountingRecoveryStore store;
  private final AccountingClient accounting;

  public FixedDepositAccountingRecoveryService(
      PaymentRepository payments,
      PaymentAttemptRepository attempts,
      PaymentStatusHistoryRepository history,
      FixedDepositAccountingRecoveryRepository recoveries,
      FixedDepositAccountingRecoveryStore store,
      AccountingClient accounting) {
    this.payments = payments;
    this.attempts = attempts;
    this.history = history;
    this.recoveries = recoveries;
    this.store = store;
    this.accounting = accounting;
  }

  public PageResponse<FixedDepositAccountingRecoveryCandidate> candidates(int page, int size) {
    Page<Payment> result = payments.findByPaymentTypeAndStatusOrderByCreatedAtAsc(
        PaymentType.FIXED_DEPOSIT_FUNDING, PaymentStatus.SETTLED, PageRequest.of(page, size));
    Page<FixedDepositAccountingRecoveryCandidate> mapped = result.map(this::candidate);
    return new PageResponse<>(mapped.getContent(), mapped.getNumber(), mapped.getSize(),
        mapped.getTotalElements(), mapped.getTotalPages());
  }

  public FixedDepositAccountingRecoveryPreview preview(String paymentId, String correlationId) {
    Payment payment = payments.findById(paymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    FixedDepositAccountingRecoveryCandidate candidate = candidate(payment);
    Optional<FixedDepositAccountingRecovery> prior = recoveries.findByPaymentId(paymentId);
    if (prior.filter(value -> "COMPLETED".equals(value.getStatus())).isPresent()) {
      return new FixedDepositAccountingRecoveryPreview(candidate, "ALREADY_RECOVERED",
          true, true, prior.get().getRecoveryJournalNumber(), List.of(), CONFIRMATION);
    }

    LinkedHashSet<String> blockers = new LinkedHashSet<>(candidate.blockers());
    if (!candidate.locallyEligible()) {
      return recoveryPreview(candidate, "BLOCKED", false, false, null, blockers);
    }

    boolean sourceRegistered = registered(candidate.sourceAccountId(), candidate.currencyCode(),
        "SOURCE_ACCOUNT", correlationId, blockers);
    boolean fixedDepositRegistered = registered(candidate.fixedDepositAccountId(),
        candidate.currencyCode(), "FIXED_DEPOSIT_ACCOUNT", correlationId, blockers);
    String action = "BLOCKED";
    String existingJournal = null;
    if (sourceRegistered && fixedDepositRegistered) {
      try {
        AccountingLookupResponse lookup = accounting.findFixedDepositByReference(
            candidate.accountingReference(), correlationId);
        List<String> mismatches = validateExisting(candidate, lookup);
        blockers.addAll(mismatches);
        if (mismatches.isEmpty()) {
          action = "ADOPT_EXISTING";
          existingJournal = lookup.journalNumber();
        }
      } catch (PeerServiceException exception) {
        if (exception.getStatus() == 404) {
          action = "CREATE";
        } else {
          blockers.add("ACCOUNTING_LOOKUP_UNAVAILABLE:" + exception.getCode());
        }
      }
    }
    if (!blockers.isEmpty()) action = "BLOCKED";
    return recoveryPreview(candidate, action, sourceRegistered, fixedDepositRegistered,
        existingJournal, blockers);
  }

  /**
   * Synchronized for same-node operator races. Cross-node safety comes from the Payments unique
   * recovery keys and Accounting's deterministic external-reference/idempotency enforcement.
   */
  public synchronized FixedDepositAccountingRecoveryResponse execute(
      String paymentId,
      String idempotencyKey,
      FixedDepositAccountingRecoveryRequest request,
      String requestedBy,
      String correlationId) {
    String keyHash = PaymentSupportService.fingerprint(idempotencyKey);
    String requestHash = requestHash(paymentId, request);
    Optional<FixedDepositAccountingRecovery> prior = recoveries.findByPaymentId(paymentId);
    if (prior.isPresent()) {
      FixedDepositAccountingRecoveryStore.assertSameCommand(prior.get(), keyHash, requestHash);
      if ("COMPLETED".equals(prior.get().getStatus())) {
        return response(prior.get(), true);
      }
    }

    String safeCorrelation = correlationId == null || correlationId.isBlank()
        ? "RECOVERY-" + UUID.randomUUID() : correlationId;
    FixedDepositAccountingRecoveryPreview preview = preview(paymentId, safeCorrelation);
    FixedDepositAccountingRecoveryCandidate candidate = preview.candidate();
    List<String> commandErrors = new ArrayList<>(preview.blockers());
    if (!request.expectedSourceFingerprint().equals(candidate.sourceFingerprint())) {
      commandErrors.add("SOURCE_FINGERPRINT_CHANGED");
    }
    if (!Objects.equals(request.expectedLegacyJournalNumber(), candidate.legacyJournalNumber())) {
      commandErrors.add("LEGACY_JOURNAL_NUMBER_CHANGED");
    }
    if (!CONFIRMATION.equals(request.confirmation())) {
      commandErrors.add("RECOVERY_CONFIRMATION_REQUIRED");
    }
    if (!Set.of("CREATE", "ADOPT_EXISTING").contains(preview.proposedAction())) {
      commandErrors.add("RECOVERY_NOT_READY:" + preview.proposedAction());
    }
    if (!commandErrors.isEmpty()) {
      throw new BusinessValidationException(List.copyOf(new LinkedHashSet<>(commandErrors)));
    }

    FixedDepositAccountingRecovery recovery = store.begin(candidate, keyHash, requestHash,
        request.reason(), requestedBy, safeCorrelation);
    try {
      AccountingResponse journal;
      String outcome;
      if ("ADOPT_EXISTING".equals(preview.proposedAction())) {
        AccountingLookupResponse lookup = accounting.findFixedDepositByReference(
            candidate.accountingReference(), safeCorrelation);
        List<String> mismatches = validateExisting(candidate, lookup);
        if (!mismatches.isEmpty()) throw new BusinessValidationException(mismatches);
        journal = lookup.journal();
        outcome = "ADOPTED";
      } else {
        journal = accounting.postFixedDeposit(request(candidate), candidate.accountingReference(),
            safeCorrelation);
        List<String> mismatches = validateJournal(candidate, journal);
        if (!mismatches.isEmpty()) throw new BusinessValidationException(mismatches);
        outcome = Boolean.TRUE.equals(journal.idempotentReplay()) ? "ADOPTED" : "POSTED";
      }
      FixedDepositAccountingRecovery completed = store.complete(recovery.getRecoveryId(),
          journal.journalNumber(), outcome, safeCorrelation);
      return response(completed, false);
    } catch (RuntimeException exception) {
      store.fail(recovery.getRecoveryId(), errorCode(exception), exception.getMessage(),
          safeCorrelation);
      throw exception;
    }
  }

  private FixedDepositAccountingRecoveryCandidate candidate(Payment payment) {
    Optional<FixedDepositAccountingRecovery> prior = recoveries.findByPaymentId(
        payment.getPaymentId());
    if (prior.filter(value -> "COMPLETED".equals(value.getStatus())).isPresent()) {
      FixedDepositAccountingRecovery value = prior.get();
      return new FixedDepositAccountingRecoveryCandidate(value.getPaymentId(),
          value.getFixedDepositId(), value.getSourceAccountId(), value.getFixedDepositAccountId(),
          value.getAmount(), value.getCurrencyCode(), value.getBusinessDate(),
          value.getOriginalOccurredAt(), value.getLegacyJournalNumber(),
          value.getAccountingReference(), true, List.of(), value.getSourceFingerprint(),
          value.getStatus(), value.getOutcome());
    }

    List<PaymentAttempt> paymentAttempts = attempts.findByPaymentIdOrderByStartedAtAsc(
        payment.getPaymentId());
    List<PaymentStatusHistory> paymentHistory = history.findByPaymentIdOrderByChangedAtAsc(
        payment.getPaymentId());
    List<String> blockers = new ArrayList<>();
    require(payment.getPaymentType() == PaymentType.FIXED_DEPOSIT_FUNDING,
        "NOT_FIXED_DEPOSIT_FUNDING", blockers);
    require(payment.getStatus() == PaymentStatus.SETTLED, "PAYMENT_NOT_SETTLED", blockers);
    require(payment.getSourceInstrumentType() == InstrumentType.DEPOSIT_ACCOUNT,
        "SOURCE_IS_NOT_DEPOSIT_ACCOUNT", blockers);
    require(payment.getDestinationInstrumentType() == InstrumentType.FIXED_DEPOSIT_ACCOUNT,
        "DESTINATION_IS_NOT_FIXED_DEPOSIT_ACCOUNT", blockers);
    require(nonBlank(payment.getFixedDepositId()), "FIXED_DEPOSIT_ID_MISSING", blockers);
    require(nonBlank(payment.getSourceAccountId()), "SOURCE_ACCOUNT_ID_MISSING", blockers);
    require(nonBlank(payment.getDestinationAccountId()), "FIXED_DEPOSIT_ACCOUNT_ID_MISSING",
        blockers);
    require(nonBlank(payment.getDepositReservationId()), "DEPOSIT_RESERVATION_EVIDENCE_MISSING",
        blockers);
    require(nonBlank(payment.getAccountingJournalNumber()), "LEGACY_JOURNAL_EVIDENCE_MISSING",
        blockers);
    require(payment.getAmount() != null && payment.getAmount().signum() > 0,
        "FUNDING_AMOUNT_INVALID", blockers);
    require(nonBlank(payment.getCurrencyCode()), "CURRENCY_MISSING", blockers);
    require(payment.getBusinessDate() != null, "BUSINESS_DATE_MISSING", blockers);
    require(payment.getReversalJournalNumber() == null && payment.getReversedAt() == null,
        "PAYMENT_WAS_REVERSED", blockers);
    if (payment.getPrincipalAmount() != null && payment.getAmount() != null) {
      require(payment.getPrincipalAmount().compareTo(payment.getAmount()) == 0,
          "PRINCIPAL_AMOUNT_MISMATCH", blockers);
    }
    if (payment.getInterestAmount() != null) {
      require(payment.getInterestAmount().compareTo(BigDecimal.ZERO) == 0,
          "FUNDING_INTEREST_MUST_BE_ZERO", blockers);
    }

    Optional<PaymentAttempt> accountingProof = paymentAttempts.stream()
        .filter(value -> "SUCCESS".equals(value.getOutcome()))
        .filter(value -> "ACCOUNTING_FD_POST".equals(value.getStepCode())
            || "ACCOUNTING_FD_LOOKUP".equals(value.getStepCode()))
        .findFirst();
    Optional<PaymentAttempt> settlementProof = paymentAttempts.stream()
        .filter(value -> "FD_FUNDING_SETTLE".equals(value.getStepCode()))
        .filter(value -> "SUCCESS".equals(value.getOutcome()))
        .findFirst();
    Optional<PaymentStatusHistory> activationProof = paymentHistory.stream()
        .filter(value -> value.getToStatus() == PaymentStatus.SETTLED)
        .filter(value -> "FIXED_DEPOSIT_ACTIVATED".equals(value.getReasonCode()))
        .findFirst();
    require(accountingProof.isPresent(), "ACCOUNTING_POST_PROOF_MISSING", blockers);
    require(settlementProof.isPresent(), "FD_SETTLEMENT_PROOF_MISSING", blockers);
    require(activationProof.isPresent(), "FD_ACTIVATION_HISTORY_MISSING", blockers);

    Instant originalOccurredAt = paymentAttempts.stream()
        .filter(value -> "ACCOUNTING_FD_POST".equals(value.getStepCode()))
        .map(PaymentAttempt::getStartedAt)
        .filter(Objects::nonNull)
        .findFirst()
        .orElseGet(() -> accountingProof.map(PaymentAttempt::getStartedAt).orElse(null));
    require(originalOccurredAt != null, "ORIGINAL_ACCOUNTING_TIME_MISSING", blockers);

    String fingerprint = blockers.contains("ORIGINAL_ACCOUNTING_TIME_MISSING")
        ? null : PaymentSupportService.fingerprint("FD_ACCOUNTING_RECOVERY_V1",
            payment.getPaymentId(), payment.getPaymentType(), payment.getStatus(),
            payment.getSourceInstrumentType(), payment.getSourceAccountId(),
            payment.getDestinationInstrumentType(), payment.getDestinationAccountId(),
            payment.getFixedDepositId(), money(payment.getAmount()), payment.getCurrencyCode(),
            payment.getDepositReservationId(), payment.getAccountingJournalNumber(),
            payment.getReversalJournalNumber(), payment.getBusinessDate(), payment.getSettledAt(),
            originalOccurredAt,
            accountingProof.map(PaymentAttempt::getStepCode).orElse(null),
            accountingProof.map(PaymentAttempt::getStartedAt).orElse(null),
            settlementProof.map(PaymentAttempt::getStartedAt).orElse(null),
            activationProof.map(PaymentStatusHistory::getChangedAt).orElse(null));
    return new FixedDepositAccountingRecoveryCandidate(payment.getPaymentId(),
        payment.getFixedDepositId(), payment.getSourceAccountId(),
        payment.getDestinationAccountId(), payment.getAmount(), payment.getCurrencyCode(),
        payment.getBusinessDate(), originalOccurredAt, payment.getAccountingJournalNumber(),
        accountingReference(payment.getPaymentId()), blockers.isEmpty(), List.copyOf(blockers),
        fingerprint, prior.map(FixedDepositAccountingRecovery::getStatus).orElse(null),
        prior.map(FixedDepositAccountingRecovery::getOutcome).orElse(null));
  }

  private boolean registered(String accountReference, String currencyCode, String label,
                             String correlationId, Set<String> blockers) {
    try {
      AccountingAccountClearanceResponse response = accounting.depositAccountClearance(
          accountReference, currencyCode, correlationId);
      if (response == null
          || !"DEPOSIT_ACCOUNT".equals(response.accountType())
          || !accountReference.equals(response.accountReference())) {
        blockers.add(label + "_CLEARANCE_MISMATCH");
        return false;
      }
      return true;
    } catch (PeerServiceException exception) {
      if (exception.getStatus() == 404) {
        blockers.add(label + "_NOT_REGISTERED_IN_ACCOUNTING");
      } else {
        blockers.add(label + "_CLEARANCE_UNAVAILABLE:" + exception.getCode());
      }
      return false;
    }
  }

  private List<String> validateExisting(FixedDepositAccountingRecoveryCandidate candidate,
                                        AccountingLookupResponse lookup) {
    List<String> errors = new ArrayList<>();
    if (lookup == null) {
      errors.add("ACCOUNTING_LOOKUP_EMPTY");
      return errors;
    }
    if (!candidate.accountingReference().equals(lookup.externalReference()))
      errors.add("ACCOUNTING_REFERENCE_MISMATCH");
    if (!"POSTED".equals(lookup.status())) errors.add("ACCOUNTING_POSTING_NOT_POSTED");
    if (!nonBlank(lookup.journalNumber())) errors.add("ACCOUNTING_JOURNAL_MISSING");
    if (lookup.journal() == null) {
      errors.add("ACCOUNTING_JOURNAL_DETAILS_MISSING");
    } else {
      errors.addAll(validateJournal(candidate, lookup.journal()));
      if (!Objects.equals(lookup.journalNumber(), lookup.journal().journalNumber()))
        errors.add("ACCOUNTING_LOOKUP_JOURNAL_MISMATCH");
    }
    return errors;
  }

  private List<String> validateJournal(FixedDepositAccountingRecoveryCandidate candidate,
                                       AccountingResponse journal) {
    List<String> errors = new ArrayList<>();
    if (journal == null) {
      errors.add("ACCOUNTING_JOURNAL_EMPTY");
      return errors;
    }
    if (!nonBlank(journal.journalNumber())) errors.add("ACCOUNTING_JOURNAL_MISSING");
    if (!candidate.accountingReference().equals(journal.externalReference()))
      errors.add("ACCOUNTING_REFERENCE_MISMATCH");
    if (!SOURCE_SERVICE.equals(journal.sourceService())) errors.add("ACCOUNTING_SOURCE_MISMATCH");
    if (!"FD_FUNDING".equals(journal.eventType())) errors.add("ACCOUNTING_EVENT_MISMATCH");
    if (!"POSTED".equals(journal.status())) errors.add("ACCOUNTING_JOURNAL_NOT_POSTED");
    if (!Objects.equals(candidate.businessDate(), journal.businessDate()))
      errors.add("ACCOUNTING_BUSINESS_DATE_MISMATCH");
    if (!Objects.equals(candidate.currencyCode(), trim(journal.currencyCode())))
      errors.add("ACCOUNTING_CURRENCY_MISMATCH");
    if (journal.totalDebit() == null
        || journal.totalDebit().compareTo(candidate.amount()) != 0)
      errors.add("ACCOUNTING_DEBIT_MISMATCH");
    if (journal.totalCredit() == null
        || journal.totalCredit().compareTo(candidate.amount()) != 0)
      errors.add("ACCOUNTING_CREDIT_MISMATCH");
    if (journal.reversesJournalNumber() != null)
      errors.add("ACCOUNTING_JOURNAL_IS_REVERSAL");
    return errors;
  }

  private FixedDepositAccountingRequest request(
      FixedDepositAccountingRecoveryCandidate candidate) {
    return new FixedDepositAccountingRequest(candidate.accountingReference(), "FUNDING",
        candidate.fixedDepositAccountId(), null, candidate.currencyCode(),
        candidate.businessDate(), OffsetDateTime.ofInstant(candidate.originalOccurredAt(),
            ZoneOffset.UTC),
        List.of(new FixedDepositAccountingComponent("PRINCIPAL",
            candidate.amount().setScale(4, RoundingMode.UNNECESSARY))),
        candidate.sourceAccountId(), null, null, "LEGACY_FD_FUNDING_RECOVERY",
        "Audited recovery of legacy fixed-deposit funding " + candidate.paymentId()
            + "; legacy journal " + candidate.legacyJournalNumber());
  }

  private FixedDepositAccountingRecoveryPreview recoveryPreview(
      FixedDepositAccountingRecoveryCandidate candidate, String action,
      boolean sourceRegistered, boolean fixedDepositRegistered, String journal,
      Set<String> blockers) {
    return new FixedDepositAccountingRecoveryPreview(candidate, action, sourceRegistered,
        fixedDepositRegistered, journal, List.copyOf(blockers), CONFIRMATION);
  }

  private String requestHash(String paymentId, FixedDepositAccountingRecoveryRequest request) {
    return PaymentSupportService.fingerprint("FD_ACCOUNTING_RECOVERY_COMMAND_V1", paymentId,
        request.expectedSourceFingerprint(), request.expectedLegacyJournalNumber(),
        request.confirmation(), request.reason());
  }

  private FixedDepositAccountingRecoveryResponse response(
      FixedDepositAccountingRecovery value, boolean replay) {
    return new FixedDepositAccountingRecoveryResponse(value.getRecoveryId(), value.getPaymentId(),
        value.getStatus(), value.getOutcome(), value.getSourceFingerprint(),
        value.getAccountingReference(), value.getLegacyJournalNumber(),
        value.getRecoveryJournalNumber(), value.getAttemptCount(), value.getRequestedBy(),
        value.getReason(), value.getCorrelationId(), value.getCreatedAt(),
        value.getLastAttemptAt(), value.getCompletedAt(), value.getErrorCode(),
        value.getErrorMessage(), replay);
  }

  private static String accountingReference(String paymentId) {
    return "PAYMENT:" + paymentId + ":ACCOUNTING";
  }

  private static String errorCode(RuntimeException exception) {
    if (exception instanceof PeerServiceException peer) return peer.getCode();
    if (exception instanceof BusinessValidationException) return "RECOVERY_VALIDATION_FAILED";
    return exception.getClass().getSimpleName();
  }

  private static String money(BigDecimal value) {
    return value == null ? null : value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }

  private static void require(boolean condition, String blocker, List<String> blockers) {
    if (!condition) blockers.add(blocker);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }
}
