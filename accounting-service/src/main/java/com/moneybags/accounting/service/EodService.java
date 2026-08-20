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
    private final Hashing hashing;
    private final AuditService audit;

    public EodService(JournalLineRepository journalLines, JournalRepository journals,
                      GlAccountRepository glAccounts, TrialBalanceRunRepository trialRuns,
                      FinancialReconciliationRunRepository reconRuns,
                      FinancialReconciliationItemRepository reconItems, AccountingPeriodRepository periods,
                      IdempotencyService idempotency, Hashing hashing, AuditService audit) {
        this.journalLines = journalLines; this.journals = journals; this.glAccounts = glAccounts;
        this.trialRuns = trialRuns; this.reconRuns = reconRuns; this.reconItems = reconItems;
        this.periods = periods; this.idempotency = idempotency; this.hashing = hashing; this.audit = audit;
    }

    public TrialBalanceResponse generateTrialBalance(TrialBalanceRequest request, String key) {
        return idempotency.execute("TRIAL_BALANCE:" + request.businessDate() + ":" + request.currencyCode(),
                key, request, TrialBalanceResponse.class, () -> generateTrialBalanceInternal(request));
    }

    @Transactional
    TrialBalanceResponse generateTrialBalanceInternal(TrialBalanceRequest request) {
        int requestedEpoch = request.executionEpoch();
        String requestHash = hashing.requestHash(request);
        List<TrialBalanceRun> snapshots = trialRuns.findLogicalRunsForUpdate(request.businessDate(),
                request.currencyCode());
        Optional<TrialBalanceRun> exact = snapshots.stream()
                .filter(value -> value.getExecutionEpoch() == requestedEpoch).findFirst();
        if (exact.isPresent()) {
            validateTrialReplay(exact.get(), request, requestHash);
            return trial(exact.get());
        }
        int latestEpoch = snapshots.stream().mapToInt(TrialBalanceRun::getExecutionEpoch).max().orElse(0);
        rejectStaleMissingAttempt("trial balance", requestedEpoch, latestEpoch);
        List<TrialBalanceRun> activeSnapshots = snapshots.stream().filter(TrialBalanceRun::isActive).toList();
        for (TrialBalanceRun prior : activeSnapshots) {
            prior.supersede();
            audit.record(prior.getId(), "SUPERSEDE_TRIAL_BALANCE", "SUCCESS", request.generatedBy(), "SERVICE",
                    correlation());
        }
        // Hibernate normally flushes INSERT actions before dirty UPDATE actions. Flush the inactive marker first so
        // Oracle's function-based unique index never observes two active snapshots for the same logical control.
        if (!activeSnapshots.isEmpty()) trialRuns.flush();
        List<Object[]> totals = journalLines.trialBalanceTotals(request.businessDate(), request.currencyCode());
        BigDecimal totalDebit = BigDecimal.ZERO.setScale(4), totalCredit = BigDecimal.ZERO.setScale(4);
        for (Object[] row : totals) {
            totalDebit = totalDebit.add((BigDecimal) row[1]); totalCredit = totalCredit.add((BigDecimal) row[2]);
        }
        TrialBalanceRun run = new TrialBalanceRun(UUID.randomUUID().toString(), request.businessDate(),
                request.currencyCode(), totalDebit.setScale(4), totalCredit.setScale(4), request.generatedBy(),
                requestedEpoch, requestHash);
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
        audit.record(run.getId(), latestEpoch == 0 ? "GENERATE_TRIAL_BALANCE" : "REFRESH_TRIAL_BALANCE", "SUCCESS",
                request.generatedBy(), "SERVICE",
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
                ? trialRuns.findByActiveTrue(PageRequest.of(page, size, Sort.by("generatedAt").descending()))
                : trialRuns.findByBusinessDateAndActiveTrue(businessDate,
                    PageRequest.of(page, size, Sort.by("generatedAt").descending()));
        return new TrialBalancePage(values.map(value -> getTrialBalance(value.getId())).getContent(), page, size,
                values.getTotalElements(), values.getTotalPages());
    }

    public FinancialReconciliationResponse reconcile(FinancialReconciliationRequest request, String key) {
        String controlDiscriminator = reconciliationControl(request);
        FinancialReconciliationResponse accepted = idempotency.execute(
                "FIN_RECON:" + request.eodRunId() + ":" + controlDiscriminator + ":" + request.currencyCode(),
                key, request,
                FinancialReconciliationResponse.class, () -> reconcileInternal(request));
        // A reconciliation run is mutable through its audited item-resolution API. Keep the original
        // request-hash validation and run identity from idempotency, but return the run's current state
        // instead of replaying the response snapshot captured before those resolutions.
        return getReconciliation(accepted.runId());
    }

    @Transactional
    FinancialReconciliationResponse reconcileInternal(FinancialReconciliationRequest request) {
        int requestedEpoch = request.executionEpoch();
        String requestHash = hashing.requestHash(request);
        String controlDiscriminator = reconciliationControl(request);
        List<FinancialReconciliationRun> snapshots = reconRuns.findLogicalRunsForUpdate(request.eodRunId(),
                controlDiscriminator, request.currencyCode());
        Optional<FinancialReconciliationRun> exact = snapshots.stream()
                .filter(value -> value.getExecutionEpoch() == requestedEpoch).findFirst();
        if (exact.isPresent()) {
            validateReconciliationReplay(exact.get(), request, requestHash);
            return reconciliation(exact.get());
        }
        int latestEpoch = snapshots.stream().mapToInt(FinancialReconciliationRun::getExecutionEpoch)
                .max().orElse(0);
        rejectStaleMissingAttempt("financial reconciliation", requestedEpoch, latestEpoch);
        List<FinancialReconciliationRun> activeSnapshots = snapshots.stream()
                .filter(FinancialReconciliationRun::isActive).toList();
        for (FinancialReconciliationRun prior : activeSnapshots) {
            for (FinancialReconciliationItem item : prior.getItems().stream()
                    .filter(FinancialReconciliationItem::isOpen).toList()) {
                item.resolve(ReconciliationItemStatus.RESOLVED,
                        "Superseded by EOD refresh execution epoch " + requestedEpoch, "SYSTEM_EOD_REFRESH");
                audit.record(item.getId(), "SUPERSEDE_RECONCILIATION_ITEM", "SUCCESS", "SYSTEM_EOD_REFRESH",
                        "SERVICE", correlation());
            }
            prior.markResolvedIfComplete();
            prior.supersede();
            audit.record(prior.getId(), "SUPERSEDE_FINANCIAL_RECONCILIATION", "SUCCESS",
                    "SYSTEM_EOD_REFRESH", "SERVICE", correlation());
        }
        if (!activeSnapshots.isEmpty()) reconRuns.flush();
        String source = request.reconciledService() == null || request.reconciledService().isBlank()
                ? "PAYMENTS-SERVICE" : request.reconciledService();
        boolean correlationScoped = request.journalCorrelationId() != null
                && !request.journalCorrelationId().isBlank();
        long actualCount = correlationScoped
                ? journals.countByBusinessDateAndCurrencyCodeAndSourceServiceAndCorrelationId(
                        request.businessDate(), request.currencyCode(), source, request.journalCorrelationId())
                : journals.countByBusinessDateAndCurrencyCodeAndSourceService(
                        request.businessDate(), request.currencyCode(), source);
        BigDecimal queriedTotal = correlationScoped
                ? journals.totalDebitByCorrelationId(request.businessDate(), request.currencyCode(), source,
                        request.journalCorrelationId())
                : journals.totalDebit(request.businessDate(), request.currencyCode(), source);
        BigDecimal actualTotal = Optional.ofNullable(queriedTotal).orElse(BigDecimal.ZERO).setScale(4);
        FinancialReconciliationRun run = new FinancialReconciliationRun(UUID.randomUUID().toString(),
                request.eodRunId(), controlDiscriminator, request.businessDate(), request.currencyCode(),
                request.expectedJournalCount(), actualCount, request.expectedTotalDebit().setScale(4), actualTotal,
                requestedEpoch, requestHash);
        if (request.expectedJournalCount() != actualCount) run.addItem(new FinancialReconciliationItem(
                UUID.randomUUID().toString(), "JOURNAL_COUNT", BigDecimal.valueOf(request.expectedJournalCount()),
                BigDecimal.valueOf(actualCount), true));
        if (request.expectedTotalDebit().setScale(4).compareTo(actualTotal) != 0)
            run.addItem(new FinancialReconciliationItem(UUID.randomUUID().toString(), "TOTAL_DEBIT",
                    request.expectedTotalDebit().setScale(4), actualTotal, true));
        reconRuns.save(run);
        audit.record(run.getId(), latestEpoch == 0 ? "RUN_FINANCIAL_RECONCILIATION"
                        : "REFRESH_FINANCIAL_RECONCILIATION",
                "SUCCESS", request.eodRunId(), "SERVICE",
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
                ? reconRuns.findByActiveTrue(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                : reconRuns.findByBusinessDateAndActiveTrue(businessDate,
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
        }
        requireEarlierPeriodsClosed(date);
        if (existing.isPresent()) return period(existing.get());
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
        requireEarlierPeriodsClosed(date);
        List<TrialBalanceRun> dateTrials = trialRuns.findByBusinessDateAndActiveTrue(date);
        if (dateTrials.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "TRIAL_BALANCE_REQUIRED",
                "A trial balance must be generated before period closure");
        if (dateTrials.stream().anyMatch(run -> !run.isBalanced())) throw new ApiException(HttpStatus.CONFLICT,
                "TRIAL_BALANCE_UNBALANCED", "An unbalanced trial balance blocks period closure");
        List<FinancialReconciliationRun> closingControls =
                reconRuns.findByEodRunIdAndBusinessDateAndActiveTrue(request.eodRunId(), date);
        requireClosingControl(closingControls, "PAYMENTS_RECONCILIATION");
        requireClosingControl(closingControls, "FIXED_DEPOSIT_RECONCILIATION");
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
        Page<String> runIds = reconRuns.findActiveEodRunIds(PageRequest.of(page, size));
        return new AccountingEodRunPage(runIds.getContent().stream().map(this::eodRun).toList(), page, size,
                runIds.getTotalElements(), runIds.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AccountingEodRunResponse getEodRun(String runId) {
        return eodRun(runId);
    }

    private AccountingEodRunResponse eodRun(String runId) {
        List<FinancialReconciliationRun> controls =
                reconRuns.findByEodRunIdAndActiveTrueOrderByControlDiscriminatorAsc(runId);
        if (controls.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,
                "EOD_RUN_NOT_FOUND", "Accounting EOD run not found");
        FinancialReconciliationRun representative = controls.getFirst();
        List<TrialBalanceRun> trials = trialRuns.findByBusinessDateAndActiveTrue(representative.getBusinessDate());
        PeriodStatus periodStatus = periods.findByBusinessDate(representative.getBusinessDate())
                .map(AccountingPeriod::getStatus).orElse(null);
        ReconciliationStatus reconciliationStatus = controls.stream()
                .anyMatch(value -> value.getStatus() == ReconciliationStatus.EXCEPTION)
                ? ReconciliationStatus.EXCEPTION
                : controls.stream().allMatch(value -> value.getStatus() == ReconciliationStatus.MATCHED)
                    ? ReconciliationStatus.MATCHED : ReconciliationStatus.RESOLVED;
        List<String> blockers = new ArrayList<>();
        if (trials.isEmpty()) blockers.add("Trial balance has not been generated");
        if (trials.stream().anyMatch(value -> !value.isBalanced())) blockers.add("Trial balance is unbalanced");
        if (reconciliationStatus == ReconciliationStatus.EXCEPTION)
            blockers.add("Reconciliation exceptions are open");
        String status = periodStatus == PeriodStatus.CLOSED ? "COMPLETED"
                : blockers.isEmpty() ? "READY_TO_CLOSE" : "BLOCKED";
        return new AccountingEodRunResponse(runId, representative.getBusinessDate(),
                representative.getCurrencyCode(), status, trials.size(), reconciliationStatus, periodStatus,
                blockers);
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

    private void validateTrialReplay(TrialBalanceRun run, TrialBalanceRequest request, String requestHash) {
        boolean matches = run.getRequestHash() != null
                ? run.getRequestHash().equals(requestHash)
                : run.getGeneratedBy().equals(request.generatedBy());
        if (!matches) throw attemptConflict("trial balance", request.executionEpoch());
    }

    private void validateReconciliationReplay(FinancialReconciliationRun run,
                                              FinancialReconciliationRequest request, String requestHash) {
        boolean matches = run.getRequestHash() != null
                ? run.getRequestHash().equals(requestHash)
                : run.getBusinessDate().equals(request.businessDate())
                    && run.getExpectedCount() == request.expectedJournalCount()
                    && run.getExpectedTotal().compareTo(request.expectedTotalDebit()) == 0;
        if (!matches) throw attemptConflict("financial reconciliation", request.executionEpoch());
    }

    private void rejectStaleMissingAttempt(String control, int requestedEpoch, int latestEpoch) {
        if (requestedEpoch <= latestEpoch) throw new ApiException(HttpStatus.CONFLICT,
                "STALE_EOD_CONTROL_ATTEMPT", "The requested " + control + " execution epoch " + requestedEpoch
                + " is older than the latest persisted epoch " + latestEpoch);
    }

    private ApiException attemptConflict(String control, int executionEpoch) {
        return new ApiException(HttpStatus.CONFLICT, "EOD_CONTROL_ATTEMPT_CONFLICT",
                "Execution epoch " + executionEpoch + " for " + control
                        + " was already used with different request content");
    }

    private void requireEarlierPeriodsClosed(LocalDate date) {
        if (periods.existsByBusinessDateBeforeAndStatusNot(date, PeriodStatus.CLOSED))
            throw new ApiException(HttpStatus.CONFLICT, "EARLIER_ACCOUNTING_PERIOD_NOT_CLOSED",
                    "Every earlier Accounting period must be closed before processing " + date);
    }

    private void requireClosingControl(List<FinancialReconciliationRun> controls, String discriminator) {
        List<FinancialReconciliationRun> matching = controls.stream()
                .filter(run -> discriminator.equals(run.getControlDiscriminator()))
                .toList();
        if (matching.isEmpty()) throw new ApiException(HttpStatus.CONFLICT,
                discriminator + "_REQUIRED",
                "An active " + discriminator + " control for this EOD run and business date is required");
        if (matching.stream().anyMatch(run -> run.getStatus() != ReconciliationStatus.MATCHED
                && run.getStatus() != ReconciliationStatus.RESOLVED))
            throw new ApiException(HttpStatus.CONFLICT, discriminator + "_NOT_CLEARED",
                    "Every active " + discriminator + " control must be MATCHED or RESOLVED");
        if (matching.stream().flatMap(run -> run.getItems().stream())
                .anyMatch(item -> item.isBlocking() && item.getStatus() == ReconciliationItemStatus.OPEN))
            throw new ApiException(HttpStatus.CONFLICT, discriminator + "_BLOCKERS_OPEN",
                    "Open blocking items remain for " + discriminator);
    }

    private String reconciliationControl(FinancialReconciliationRequest request) {
        if (request.stepCode() != null && !request.stepCode().isBlank())
            return request.stepCode().trim().toUpperCase(Locale.ROOT);
        String source = request.reconciledService() == null || request.reconciledService().isBlank()
                ? "PAYMENTS-SERVICE" : request.reconciledService().trim().toUpperCase(Locale.ROOT);
        return switch (source) {
            case "PAYMENTS-SERVICE" -> "PAYMENTS_RECONCILIATION";
            case "DEPOSIT-ACCOUNT-SERVICE" -> "FIXED_DEPOSIT_RECONCILIATION";
            default -> source;
        };
    }
}
