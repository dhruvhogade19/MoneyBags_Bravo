package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.*;
import com.moneybags.accounting.entity.*;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.*;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class EodService {
    private final JournalLineRepository journalLines;
    private final JournalRepository journals;
    private final GlAccountRepository glAccounts;
    private final TrialBalanceRunRepository trialRuns;
    private final FinancialReconciliationRunRepository reconRuns;
    private final FinancialReconciliationItemRepository reconItems;
    private final AccountingPeriodRepository periods;
    private final IdempotencyService idempotency;
    private final AuditService audit;

    public EodService(JournalLineRepository journalLines, JournalRepository journals,
                      GlAccountRepository glAccounts, TrialBalanceRunRepository trialRuns,
                      FinancialReconciliationRunRepository reconRuns,
                      FinancialReconciliationItemRepository reconItems, AccountingPeriodRepository periods,
                      IdempotencyService idempotency, AuditService audit) {
        this.journalLines = journalLines; this.journals = journals; this.glAccounts = glAccounts;
        this.trialRuns = trialRuns; this.reconRuns = reconRuns; this.reconItems = reconItems;
        this.periods = periods; this.idempotency = idempotency; this.audit = audit;
    }

    public TrialBalanceResponse generateTrialBalance(TrialBalanceRequest request, String key) {
        return idempotency.execute("TRIAL_BALANCE:" + request.businessDate() + ":" + request.currencyCode(),
                key, request, TrialBalanceResponse.class, () -> generateTrialBalanceInternal(request));
    }

    @Transactional
    TrialBalanceResponse generateTrialBalanceInternal(TrialBalanceRequest request) {
        Optional<TrialBalanceRun> existing = trialRuns.findByBusinessDateAndCurrencyCode(request.businessDate(),
                request.currencyCode());
        if (existing.isPresent()) return trial(existing.get());
        List<Object[]> totals = journalLines.trialBalanceTotals(request.businessDate(), request.currencyCode());
        BigDecimal totalDebit = BigDecimal.ZERO.setScale(4), totalCredit = BigDecimal.ZERO.setScale(4);
        for (Object[] row : totals) {
            totalDebit = totalDebit.add((BigDecimal) row[1]); totalCredit = totalCredit.add((BigDecimal) row[2]);
        }
        TrialBalanceRun run = new TrialBalanceRun(UUID.randomUUID().toString(), request.businessDate(),
                request.currencyCode(), totalDebit.setScale(4), totalCredit.setScale(4), request.generatedBy());
        for (Object[] row : totals) {
            String glCode = (String) row[0]; BigDecimal debit = ((BigDecimal) row[1]).setScale(4);
            BigDecimal credit = ((BigDecimal) row[2]).setScale(4);
            GlAccount gl = glAccounts.findByGlCode(glCode).orElseThrow();
            BigDecimal closing = gl.getNormalBalance() == NormalBalance.DEBIT
                    ? debit.subtract(credit) : credit.subtract(debit);
            run.addLine(new TrialBalanceLine(UUID.randomUUID().toString(), glCode, debit, credit,
                    closing.setScale(4)));
        }
        trialRuns.save(run);
        audit.record(run.getId(), "GENERATE_TRIAL_BALANCE", "SUCCESS", request.generatedBy(), "SERVICE",
                correlation());
        return trial(run);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse getTrialBalance(String runId) {
        return trial(trialRuns.findDetailedById(runId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "TRIAL_BALANCE_NOT_FOUND", "Trial balance run not found")));
    }

    @Transactional(readOnly = true)
    public TrialBalancePage listTrialBalances(LocalDate businessDate, int page, int size) {
        Page<TrialBalanceRun> values = businessDate == null
                ? trialRuns.findAll(PageRequest.of(page, size, Sort.by("generatedAt").descending()))
                : trialRuns.findByBusinessDate(businessDate,
                    PageRequest.of(page, size, Sort.by("generatedAt").descending()));
        return new TrialBalancePage(values.map(value -> getTrialBalance(value.getId())).getContent(), page, size,
                values.getTotalElements(), values.getTotalPages());
    }

    public FinancialReconciliationResponse reconcile(FinancialReconciliationRequest request, String key) {
        return idempotency.execute("FIN_RECON:" + request.eodRunId() + ":" + request.currencyCode(), key, request,
                FinancialReconciliationResponse.class, () -> reconcileInternal(request));
    }

    @Transactional
    FinancialReconciliationResponse reconcileInternal(FinancialReconciliationRequest request) {
        Optional<FinancialReconciliationRun> existing = reconRuns.findByEodRunIdAndCurrencyCode(request.eodRunId(),
                request.currencyCode());
        if (existing.isPresent()) return reconciliation(existing.get());
        String source = request.reconciledService() == null || request.reconciledService().isBlank()
                ? "PAYMENTS-SERVICE" : request.reconciledService();
        long actualCount = journals.countByBusinessDateAndCurrencyCodeAndSourceService(request.businessDate(),
                request.currencyCode(), source);
        BigDecimal actualTotal = Optional.ofNullable(journals.totalDebit(request.businessDate(),
                request.currencyCode(), source)).orElse(BigDecimal.ZERO).setScale(4);
        FinancialReconciliationRun run = new FinancialReconciliationRun(UUID.randomUUID().toString(),
                request.eodRunId(), request.businessDate(), request.currencyCode(), request.expectedJournalCount(),
                actualCount, request.expectedTotalDebit().setScale(4), actualTotal);
        if (request.expectedJournalCount() != actualCount) run.addItem(new FinancialReconciliationItem(
                UUID.randomUUID().toString(), "JOURNAL_COUNT", BigDecimal.valueOf(request.expectedJournalCount()),
                BigDecimal.valueOf(actualCount), true));
        if (request.expectedTotalDebit().setScale(4).compareTo(actualTotal) != 0)
            run.addItem(new FinancialReconciliationItem(UUID.randomUUID().toString(), "TOTAL_DEBIT",
                    request.expectedTotalDebit().setScale(4), actualTotal, true));
        reconRuns.save(run);
        audit.record(run.getId(), "RUN_FINANCIAL_RECONCILIATION", "SUCCESS", request.eodRunId(), "SERVICE",
                correlation());
        return reconciliation(run);
    }

    @Transactional(readOnly = true)
    public FinancialReconciliationResponse getReconciliation(String runId) {
        return reconciliation(reconRuns.findDetailedById(runId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "RECONCILIATION_NOT_FOUND", "Financial reconciliation run not found")));
    }

    @Transactional(readOnly = true)
    public FinancialReconciliationPage listReconciliations(LocalDate businessDate, int page, int size) {
        Page<FinancialReconciliationRun> values = businessDate == null
                ? reconRuns.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : reconRuns.findByBusinessDate(businessDate,
                    PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new FinancialReconciliationPage(values.map(value -> getReconciliation(value.getId())).getContent(),
                page, size, values.getTotalElements(), values.getTotalPages());
    }

    public FinancialReconciliationResponse resolve(String runId, ReconciliationRunResolutionRequest request,
                                                    String key) {
        return resolve(runId, request.itemId(), new ReconciliationResolutionRequest(request.status(),
                request.resolution(), request.actorId()), key);
    }

    public FinancialReconciliationResponse resolve(String runId, String itemId,
                                                    ReconciliationResolutionRequest request, String key) {
        return idempotency.execute("FIN_RECON_RESOLUTION:" + itemId, key, request,
                FinancialReconciliationResponse.class, () -> resolveInternal(runId, itemId, request));
    }

    @Transactional
    FinancialReconciliationResponse resolveInternal(String runId, String itemId,
                                                     ReconciliationResolutionRequest request) {
        if (request.status() == ReconciliationItemStatus.OPEN) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_RESOLUTION_STATUS", "Resolution status must be RESOLVED or ACCEPTED");
        FinancialReconciliationRun run = reconRuns.findDetailedById(runId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "RECONCILIATION_NOT_FOUND", "Financial reconciliation run not found"));
        FinancialReconciliationItem item = run.getItems().stream().filter(value -> value.getId().equals(itemId))
                .findFirst().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "RECONCILIATION_ITEM_NOT_FOUND", "Reconciliation item not found in the supplied run"));
        item.resolve(request.status(), request.resolution(), request.actorId()); run.markResolvedIfComplete();
        audit.record(itemId, "RESOLVE_RECONCILIATION_ITEM", "SUCCESS", request.actorId(), "USER", correlation());
        return reconciliation(run);
    }

    public AccountingPeriodResponse openPeriod(LocalDate date, AccountingPeriodCommand request, String key) {
        return idempotency.execute("ACCOUNTING_PERIOD_OPEN:" + date, key, request, AccountingPeriodResponse.class,
                () -> openPeriodInternal(date, request));
    }

    @Transactional
    AccountingPeriodResponse openPeriodInternal(LocalDate date, AccountingPeriodCommand request) {
        Optional<AccountingPeriod> existing = periods.findByBusinessDateForUpdate(date);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == PeriodStatus.CLOSED) throw new ApiException(HttpStatus.CONFLICT,
                    "ACCOUNTING_PERIOD_ALREADY_CLOSED", "A closed period cannot be reopened in the current scope");
            return period(existing.get());
        }
        AccountingPeriod value = periods.save(new AccountingPeriod(UUID.randomUUID().toString(), date,
                request.actorId()));
        audit.record(value.getId(), "OPEN_ACCOUNTING_PERIOD", "SUCCESS", request.actorId(), "SERVICE", correlation());
        return period(value);
    }

    public AccountingPeriodResponse closePeriod(LocalDate date, AccountingPeriodCommand request, String key) {
        return idempotency.execute("ACCOUNTING_PERIOD_CLOSE:" + date, key, request, AccountingPeriodResponse.class,
                () -> closePeriodInternal(date, request));
    }

    @Transactional
    AccountingPeriodResponse closePeriodInternal(LocalDate date, AccountingPeriodCommand request) {
        AccountingPeriod value = periods.findByBusinessDateForUpdate(date).orElseThrow(() -> new ApiException(
                HttpStatus.CONFLICT, "ACCOUNTING_PERIOD_NOT_OPEN", "The Accounting period must be opened first"));
        if (value.getStatus() == PeriodStatus.CLOSED) return period(value);
        List<TrialBalanceRun> dateTrials = trialRuns.findByBusinessDate(date);
        if (dateTrials.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "TRIAL_BALANCE_REQUIRED",
                "A trial balance must be generated before period closure");
        if (dateTrials.stream().anyMatch(run -> !run.isBalanced())) throw new ApiException(HttpStatus.CONFLICT,
                "TRIAL_BALANCE_UNBALANCED", "An unbalanced trial balance blocks period closure");
        if (reconItems.countBlockingForDate(date, ReconciliationItemStatus.OPEN) > 0)
            throw new ApiException(HttpStatus.CONFLICT, "RECONCILIATION_BLOCKERS_OPEN",
                    "Unresolved blocking reconciliation items prevent period closure");
        value.close(request.actorId());
        audit.record(value.getId(), "CLOSE_ACCOUNTING_PERIOD", "SUCCESS", request.actorId(), "SERVICE", correlation());
        return period(value);
    }

    @Transactional(readOnly = true)
    public AccountingPeriodResponse getPeriod(LocalDate date) {
        return period(periods.findByBusinessDate(date).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "ACCOUNTING_PERIOD_NOT_FOUND", "Accounting period not found")));
    }

    @Transactional(readOnly = true)
    public AccountingEodRunPage listEodRuns(int page, int size) {
        Page<FinancialReconciliationRun> values = reconRuns.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return new AccountingEodRunPage(values.map(this::eodRun).getContent(), page, size,
                values.getTotalElements(), values.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AccountingEodRunResponse getEodRun(String runId) {
        return eodRun(reconRuns.findTopByEodRunIdOrderByCreatedAtDesc(runId).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "EOD_RUN_NOT_FOUND", "Accounting EOD run not found")));
    }

    private AccountingEodRunResponse eodRun(FinancialReconciliationRun run) {
        List<TrialBalanceRun> trials = trialRuns.findByBusinessDate(run.getBusinessDate());
        PeriodStatus periodStatus = periods.findByBusinessDate(run.getBusinessDate())
                .map(AccountingPeriod::getStatus).orElse(null);
        List<String> blockers = new ArrayList<>();
        if (trials.isEmpty()) blockers.add("Trial balance has not been generated");
        if (trials.stream().anyMatch(value -> !value.isBalanced())) blockers.add("Trial balance is unbalanced");
        if (run.getStatus() == ReconciliationStatus.EXCEPTION) blockers.add("Reconciliation exceptions are open");
        String status = periodStatus == PeriodStatus.CLOSED ? "COMPLETED"
                : blockers.isEmpty() ? "READY_TO_CLOSE" : "BLOCKED";
        return new AccountingEodRunResponse(run.getEodRunId(), run.getBusinessDate(), run.getCurrencyCode(),
                status, trials.size(), run.getStatus(), periodStatus, blockers);
    }

    private TrialBalanceResponse trial(TrialBalanceRun value) {
        return new TrialBalanceResponse(value.getId(), value.getBusinessDate(), value.getCurrencyCode(),
                value.getTotalDebit(), value.getTotalCredit(), value.isBalanced(), value.getGeneratedBy(),
                value.getGeneratedAt(), value.getLines().stream().map(line -> new TrialBalanceLineResponse(
                line.getGlCode(), line.getDebitTotal(), line.getCreditTotal(), line.getClosingBalance())).toList());
    }
    private FinancialReconciliationResponse reconciliation(FinancialReconciliationRun value) {
        return new FinancialReconciliationResponse(value.getId(), value.getEodRunId(), value.getBusinessDate(),
                value.getCurrencyCode(), value.getExpectedCount(), value.getActualCount(), value.getExpectedTotal(),
                value.getActualTotal(), value.getStatus(), value.getItems().stream().map(item ->
                new ReconciliationItemResponse(item.getId(), item.getReference(), item.getExpectedAmount(),
                        item.getActualAmount(), item.getDifference(), item.isBlocking(), item.getStatus(),
                        item.getResolution(), item.getResolvedBy(), item.getResolvedAt())).toList());
    }
    private AccountingPeriodResponse period(AccountingPeriod value) { return new AccountingPeriodResponse(
            value.getBusinessDate(), value.getStatus(), value.getOpenedAt(), value.getClosedAt(), value.getOpenedBy(),
            value.getClosedBy(), value.getVersion()); }
    private String correlation() { return MDC.get("correlationId") == null ? "unknown" : MDC.get("correlationId"); }
}
