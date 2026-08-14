package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.entity.*;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.*;
import com.moneybags.accounting.service.PostingLineFactory.LineDraft;
import com.moneybags.accounting.service.PostingLineFactory.PostingPlan;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

@Service
public class PostingService {
    private final PostingRequestRepository postingRequests;
    private final JournalRepository journals;
    private final AccountingPeriodRepository periods;
    private final PostingLineFactory lineFactory;
    private final LifecycleService lifecycle;
    private final Hashing hashing;
    private final JournalMapper mapper;
    private final AuditService audit;

    public PostingService(PostingRequestRepository postingRequests, JournalRepository journals,
                          AccountingPeriodRepository periods, PostingLineFactory lineFactory,
                          LifecycleService lifecycle, Hashing hashing, JournalMapper mapper, AuditService audit) {
        this.postingRequests = postingRequests; this.journals = journals; this.periods = periods;
        this.lineFactory = lineFactory; this.lifecycle = lifecycle; this.hashing = hashing;
        this.mapper = mapper; this.audit = audit;
    }

    @Transactional
    public synchronized JournalResponse postPayment(PaymentSettlementPostingRequest request, String key,
                                                     String correlationId, String sourceService) {
        String external = "PAYMENT:" + request.paymentId() + ":ACCOUNTING";
        return post(external, key, request, correlationId, sourceService, () ->
                new PlanWithReversal(lineFactory.payment(request), null));
    }

    @Transactional
    public synchronized JournalResponse postBill(BillAccountingPostingRequest request, String key,
                                                  String correlationId, String sourceService) {
        String external = "BILL:" + request.billId() + ":ACCOUNTING";
        return post(external, key, request, correlationId, sourceService, () ->
                new PlanWithReversal(lineFactory.bill(request), null));
    }

    @Transactional
    public synchronized JournalResponse postFixedDeposit(FixedDepositPostingRequest request, String key,
                                                          String correlationId, String sourceService,
                                                          String forcedType) {
        if (forcedType == null && (request.postingType() == null || request.postingType().isBlank()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "POSTING_TYPE_REQUIRED",
                    "postingType is required on the generic Fixed Deposit endpoint");
        FixedDepositPostingRequest normalized = forcedType == null ? request : new FixedDepositPostingRequest(
                request.postingReference(), forcedType, request.fixedDepositAccountId(), request.productCode(),
                request.currencyCode(), request.businessDate(), request.occurredAt(), request.components(),
                request.fundingAccountId(), request.payoutAccountId(), request.payoutMode(), request.reasonCode(),
                request.narration());
        return post(normalized.postingReference(), key, normalized, correlationId, sourceService, () ->
                new PlanWithReversal(lineFactory.fixedDeposit(normalized), null));
    }

    @Transactional
    public synchronized JournalResponse postRefund(PaymentRefundPostingRequest request, String key,
                                                    String correlationId, String sourceService) {
        String external = "REFUND:" + request.refundId() + ":ACCOUNTING";
        return post(external, key, request, correlationId, sourceService, () -> {
            Journal original = loadJournal(request.originalJournalNumber());
            if (!original.getCurrencyCode().trim().equals(request.currencyCode()))
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "REFUND_CURRENCY_MISMATCH",
                        "Refund currency must match the original journal");
            BigDecimal amount = PostingLineFactory.money(request.amount());
            BigDecimal available = original.getTotalDebit().subtract(orZero(journals.totalReversed(
                    original.getJournalNumber()))).setScale(4);
            if (amount.compareTo(available) > 0) throw new ApiException(HttpStatus.CONFLICT,
                    "REFUND_EXCEEDS_REVERSIBLE_AMOUNT", "Refund exceeds the remaining reversible journal amount");
            List<LineDraft> opposite = oppositeLines(original, amount, "PAYMENT_REFUND_V1", request.reason());
            PostingPlan plan = new PostingPlan("PAYMENT_REFUND", request.currencyCode(), request.businessDate(),
                    request.occurredAt(), Map.of(), opposite);
            return new PlanWithReversal(plan, original.getJournalNumber());
        });
    }

    @Transactional
    public synchronized JournalResponse reverse(String journalNumber, JournalReversalRequest request, String key,
                                                String correlationId, String sourceService) {
        if (key.length() > 130) throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_TOO_LONG",
                "Idempotency-Key must not exceed 130 characters for reversal commands");
        String external = "REVERSAL:" + key;
        return post(external, key, request, correlationId, sourceService, () -> {
            Journal original = loadJournal(journalNumber);
            BigDecimal already = orZero(journals.totalReversed(journalNumber));
            if (already.signum() > 0) throw new ApiException(HttpStatus.CONFLICT,
                    "JOURNAL_ALREADY_REVERSED", "The journal has already been fully or partially reversed");
            List<LineDraft> opposite = oppositeLines(original, original.getTotalDebit(), "JOURNAL_REVERSAL_V1",
                    request.reason());
            PostingPlan plan = new PostingPlan("JOURNAL_REVERSAL", original.getCurrencyCode().trim(),
                    request.businessDate(), request.occurredAt(), Map.of(), opposite);
            return new PlanWithReversal(plan, original.getJournalNumber());
        });
    }

    @Transactional(readOnly = true)
    public PostingOutcomeResponse outcome(String externalReference) {
        PostingRequest request = postingRequests.findByExternalReference(externalReference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POSTING_NOT_FOUND",
                        "No posting exists for the supplied external reference"));
        JournalResponse journal = request.getJournalNumber() == null ? null
                : mapper.toResponse(loadJournal(request.getJournalNumber()), false);
        return new PostingOutcomeResponse(request.getExternalReference(), request.getStatus().name(),
                request.getJournalNumber(), request.getReceivedAt(), request.getCompletedAt(), journal);
    }

    private JournalResponse post(String externalReference, String idempotencyKey, Object request,
                                 String correlationId, String sourceService,
                                 Supplier<PlanWithReversal> planSupplier) {
        String requestHash = hashing.requestHash(request);
        String keyHash = hashing.requestHash(idempotencyKey);
        Optional<PostingRequest> existing = postingRequests.findByExternalReference(externalReference);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(requestHash)
                    || !existing.get().getIdempotencyKeyHash().equals(keyHash)) throw new ApiException(HttpStatus.CONFLICT,
                    "EXTERNAL_REFERENCE_REUSED", "The posting reference was reused with different request content");
            if (existing.get().getJournalNumber() == null) throw new ApiException(HttpStatus.CONFLICT,
                    "POSTING_IN_PROGRESS", "The posting is still being processed");
            return mapper.toResponse(loadJournal(existing.get().getJournalNumber()), true);
        }
        postingRequests.findByIdempotencyKeyHash(keyHash).ifPresent(duplicate -> {
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                    "The Idempotency-Key was already used for a different posting reference");
        });

        PlanWithReversal planned = planSupplier.get();
        PostingPlan plan = planned.plan();
        ensurePeriodOpen(plan.businessDate());
        lifecycle.lockAndValidateForPosting(plan.accountReferences());
        validatePlan(plan);

        PostingRequest posting = postingRequests.save(new PostingRequest(UUID.randomUUID().toString(),
                externalReference, keyHash, requestHash, sourceService, plan.eventType()));
        long sequence = journals.nextPostingSequence();
        String journalNumber = "JRN-" + plan.businessDate().toString().replace("-", "") + "-"
                + String.format("%08d", sequence);
        BigDecimal debit = plan.lines().stream().map(LineDraft::debit).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4);
        BigDecimal credit = plan.lines().stream().map(LineDraft::credit).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4);
        Journal journal = new Journal(UUID.randomUUID().toString(), journalNumber, sequence, externalReference,
                sourceService, plan.eventType(), plan.occurredAt(), plan.businessDate(), plan.currencyCode(),
                debit, credit, planned.reversesJournalNumber(), correlationId);
        int lineNumber = 1;
        for (LineDraft line : plan.lines()) journal.addLine(new JournalLine(UUID.randomUUID().toString(), lineNumber++,
                line.glCode(), line.subledgerReference(), line.componentType(), line.ruleCode(), line.ruleVersion(),
                line.debit(), line.credit(), line.narration()));
        journals.save(journal);
        posting.posted(journalNumber);
        audit.record(journalNumber, "POST_" + plan.eventType(), "SUCCESS", sourceService, "SERVICE", correlationId);
        return mapper.toResponse(journal, false);
    }

    private void validatePlan(PostingPlan plan) {
        if (plan.lines().size() < 2) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "INSUFFICIENT_JOURNAL_LINES", "A journal requires at least one debit and one credit line");
        BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
        for (LineDraft line : plan.lines()) {
            if (line.debit().signum() < 0 || line.credit().signum() < 0
                    || (line.debit().signum() > 0) == (line.credit().signum() > 0))
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_JOURNAL_LINE",
                        "Every journal line must have exactly one positive debit or credit amount");
            debit = debit.add(line.debit()); credit = credit.add(line.credit());
        }
        if (debit.setScale(4).compareTo(credit.setScale(4)) != 0 || debit.signum() <= 0)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNBALANCED_JOURNAL",
                    "Total debit must equal total credit and be greater than zero");
    }

    private void ensurePeriodOpen(LocalDate businessDate) {
        periods.findByBusinessDate(businessDate).ifPresent(period -> {
            if (period.getStatus() == com.moneybags.accounting.domain.DomainTypes.PeriodStatus.CLOSED)
                throw new ApiException(HttpStatus.CONFLICT, "ACCOUNTING_PERIOD_CLOSED",
                        "Posting is not allowed for a closed Accounting business date");
        });
    }

    private Journal loadJournal(String journalNumber) {
        return journals.findByJournalNumber(journalNumber).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "JOURNAL_NOT_FOUND", "Journal not found: " + journalNumber));
    }

    private List<LineDraft> oppositeLines(Journal original, BigDecimal requestedAmount, String ruleCode,
                                          String narration) {
        List<JournalLine> originalDebits = original.getLines().stream()
                .filter(line -> line.getDebitAmount().signum() > 0).toList();
        List<JournalLine> originalCredits = original.getLines().stream()
                .filter(line -> line.getCreditAmount().signum() > 0).toList();
        List<LineDraft> result = new ArrayList<>();
        allocateOpposite(originalCredits, requestedAmount, original.getTotalCredit(), true, ruleCode, narration,
                result);
        allocateOpposite(originalDebits, requestedAmount, original.getTotalDebit(), false, ruleCode, narration,
                result);
        return result;
    }

    private void allocateOpposite(List<JournalLine> source, BigDecimal requested, BigDecimal total,
                                  boolean createDebit, String ruleCode, String narration, List<LineDraft> target) {
        BigDecimal remaining = requested;
        for (int index = 0; index < source.size(); index++) {
            JournalLine original = source.get(index);
            BigDecimal originalAmount = createDebit ? original.getCreditAmount() : original.getDebitAmount();
            BigDecimal amount = index == source.size() - 1 ? remaining : requested.multiply(originalAmount)
                    .divide(total, 4, RoundingMode.HALF_EVEN).min(remaining);
            remaining = remaining.subtract(amount);
            if (amount.signum() == 0) continue;
            target.add(new LineDraft(original.getGlCode(), original.getSubledgerReference(),
                    original.getComponentType(), ruleCode, 1, createDebit ? amount : PostingLineFactory.ZERO,
                    createDebit ? PostingLineFactory.ZERO : amount, narration));
        }
    }

    private BigDecimal orZero(BigDecimal value) { return value == null ? PostingLineFactory.ZERO : value.setScale(4); }
    private record PlanWithReversal(PostingPlan plan, String reversesJournalNumber) {}
}
