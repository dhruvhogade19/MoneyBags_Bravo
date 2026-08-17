package com.moneybags.eod;

import com.moneybags.eod.EodController.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
class EodOrchestrationService {
    static final List<StepDefinition> STEPS = List.of(
            new StepDefinition("PAYMENTS_CUTOFF", 1, "payments-service", "POST", "/internal/v1/payments/eod/cutoff"),
            new StepDefinition("PAYMENTS_DRAIN", 2, "payments-service", "POST", "/internal/v1/payments/eod/drain"),
            new StepDefinition("CREDIT_CARD_READINESS", 3, "credit-card-service", "GET", "/internal/v1/credit-card-accounts/eod/readiness"),
            new StepDefinition("DEPOSIT_READINESS", 4, "deposit-account-service", "GET", "/internal/v1/deposit-accounts/eod/readiness"),
            new StepDefinition("DEPOSIT_ACCRUALS", 5, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/accruals"),
            new StepDefinition("FIXED_DEPOSIT_READINESS", 6, "deposit-account-service", "GET", "/internal/v1/deposit-accounts/eod/fixed-deposit-readiness"),
            new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/fixed-deposit-accruals"),
            new StepDefinition("FIXED_DEPOSIT_MATURITIES", 8, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/fixed-deposit-maturities"),
            new StepDefinition("BILLS_CLOSE", 9, "bill-generation-service", "POST", "/internal/v1/bills/eod/close"),
            new StepDefinition("TRIAL_BALANCE", 10, "accounting-service", "POST", "/internal/v1/trial-balances"),
            new StepDefinition("PAYMENTS_RECONCILIATION", 11, "accounting-service", "POST", "/internal/v1/eod/reconciliation/runs"),
            new StepDefinition("FIXED_DEPOSIT_RECONCILIATION", 12, "accounting-service", "POST", "/internal/v1/accounting/fixed-deposit-reconciliation"),
            new StepDefinition("STATEMENTS_GENERATE", 13, "statements-service", "POST", "/internal/v1/statements/eod/generate"),
            new StepDefinition("NOTIFICATIONS_SEND", 14, "notification-service", "POST", "/internal/v1/notifications"),
            new StepDefinition("ACCOUNTING_PERIOD_CLOSE", 15, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/close"),
            new StepDefinition("ACCOUNTING_PERIOD_OPEN", 16, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/open")
    );

    private final PeerOperations peers;
    private final String currency;
    private final Map<String, RunState> runs = new LinkedHashMap<>();
    private final Map<String, String> idempotency = new HashMap<>();
    private LocalDate businessDate;
    private String dateStatus = "OPEN";
    private long dateVersion = 1;

    EodOrchestrationService(PeerOperations peers,
                            @Value("${moneybags.eod.initial-business-date:2026-08-13}") LocalDate initialDate,
                            @Value("${moneybags.eod.currency:INR}") String currency) {
        this.peers = peers; this.businessDate = initialDate; this.currency = currency;
    }

    synchronized BusinessDateResponse businessDate() {
        return new BusinessDateResponse(businessDate, dateStatus, null, null, null, dateVersion);
    }

    synchronized EodRunResponse start(String key, StartEodRunRequest request) {
        String priorRunId = idempotency.get(key);
        if (priorRunId != null) return response(runs.get(priorRunId));
        LocalDate requestedDate = request.businessDate() == null ? businessDate : request.businessDate();
        if (!requestedDate.equals(businessDate) || !"OPEN".equals(dateStatus))
            throw new EodConflictException("Business date is not open for EOD processing: " + requestedDate);
        RunState run = new RunState(UUID.randomUUID().toString(), requestedDate, request.startedBy());
        STEPS.forEach(step -> run.steps.add(new StepState(step)));
        runs.put(run.id, run); idempotency.put(key, run.id); dateStatus = "EOD_IN_PROGRESS";
        executeFrom(run, 0);
        return response(run);
    }

    synchronized EodRunResponse get(String runId) { return response(requireRun(runId)); }

    synchronized EodRunResponse resume(String runId) {
        RunState run = requireRun(runId);
        int firstIncomplete = firstIncomplete(run);
        if (firstIncomplete < run.steps.size()) executeFrom(run, firstIncomplete);
        return response(run);
    }

    synchronized EodRunResponse retry(String runId, String stepCode) {
        RunState run = requireRun(runId);
        int index = indexOf(stepCode);
        if (index < 0) throw new EodNotFoundException("EOD step not found: " + stepCode);
        executeFrom(run, index);
        return response(run);
    }

    synchronized EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request) {
        for (RunState run : runs.values()) {
            for (ExceptionState exception : run.exceptions) {
                if (exception.id.equals(exceptionId)) {
                    exception.status = request.waived() ? "WAIVED" : "RESOLVED";
                    exception.resolution = request.resolution(); exception.resolvedBy = request.resolvedBy();
                    exception.resolvedAt = Instant.now(); return response(run);
                }
            }
        }
        throw new EodNotFoundException("EOD exception not found: " + exceptionId);
    }

    synchronized BusinessDateResponse openNext(OpenBusinessDateRequest request) {
        LocalDate next = request.businessDate() == null ? businessDate.plusDays(1) : request.businessDate();
        if (!next.isAfter(businessDate)) throw new EodConflictException("The next business date must be after the current date");
        businessDate = next; dateStatus = "OPEN"; dateVersion++; return businessDate();
    }

    private void executeFrom(RunState run, int start) {
        run.status = "RUNNING"; run.completedAt = null;
        EodContext context = new EodContext(run.id, run.businessDate, run.startedBy, currency);
        for (int i = start; i < run.steps.size(); i++) {
            StepState state = run.steps.get(i);
            if (state.status.equals("COMPLETED")) continue;
            state.status = "RUNNING"; state.startedAt = Instant.now(); state.attemptCount++;
            try {
                state.output = peers.execute(state.definition, context, completedOutputs(run));
                state.status = "COMPLETED"; state.completedAt = Instant.now(); state.errorCode = null; state.message = null;
                run.exceptions.stream().filter(e -> e.stepCode.equals(state.definition.code()) && e.status.equals("OPEN"))
                        .forEach(e -> { e.status = "RESOLVED"; e.resolution = "Step retry completed"; e.resolvedBy = "SYSTEM"; e.resolvedAt = Instant.now(); });
            } catch (PeerOperationException exception) {
                state.status = "FAILED"; state.completedAt = Instant.now(); state.errorCode = exception.code();
                state.message = exception.getMessage(); run.status = "FAILED"; dateStatus = "EOD_FAILED";
                run.exceptions.add(new ExceptionState(state.definition.code(), exception.code(), exception.getMessage(), exception.details()));
                return;
            } catch (RuntimeException exception) {
                state.status = "FAILED"; state.completedAt = Instant.now(); state.errorCode = "UPSTREAM_ERROR";
                state.message = exception.getMessage(); run.status = "FAILED"; dateStatus = "EOD_FAILED";
                run.exceptions.add(new ExceptionState(state.definition.code(), "UPSTREAM_ERROR", exception.getMessage(), Map.of()));
                return;
            }
        }
        run.status = "COMPLETED"; run.completedAt = Instant.now();
        businessDate = run.businessDate.plusDays(1); dateStatus = "OPEN"; dateVersion++;
    }

    private Map<String, Map<String, Object>> completedOutputs(RunState run) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        run.steps.stream().filter(step -> step.status.equals("COMPLETED"))
                .forEach(step -> result.put(step.definition.code(), step.output));
        return result;
    }

    private int firstIncomplete(RunState run) {
        for (int i = 0; i < run.steps.size(); i++) if (!run.steps.get(i).status.equals("COMPLETED")) return i;
        return run.steps.size();
    }

    private int indexOf(String stepCode) {
        for (int i = 0; i < STEPS.size(); i++) if (STEPS.get(i).code().equalsIgnoreCase(stepCode)) return i;
        return -1;
    }

    private RunState requireRun(String runId) {
        return Optional.ofNullable(runs.get(runId)).orElseThrow(() -> new EodNotFoundException("EOD run not found: " + runId));
    }

    private EodRunResponse response(RunState run) {
        return new EodRunResponse(run.id, run.businessDate, run.status, run.startedBy, run.startedAt, run.completedAt,
                run.steps.stream().map(step -> new StepResponse(step.definition.code(), step.definition.sequence(),
                        step.definition.providerService(), step.definition.method(), step.definition.path(), step.status,
                        run.id + ":" + step.definition.code(), step.attemptCount, step.startedAt, step.completedAt,
                        step.errorCode, step.message, step.output)).toList(),
                run.exceptions.stream().map(value -> new ExceptionResponse(value.id, value.stepCode, "ERROR",
                        value.errorCode, value.details, value.status, value.resolution, value.resolvedBy,
                        value.resolvedAt)).toList(), 1);
    }

    private static final class RunState {
        final String id; final LocalDate businessDate; final String startedBy; final Instant startedAt = Instant.now();
        final List<StepState> steps = new ArrayList<>(); final List<ExceptionState> exceptions = new ArrayList<>();
        String status = "PENDING"; Instant completedAt;
        RunState(String id, LocalDate businessDate, String startedBy) { this.id = id; this.businessDate = businessDate; this.startedBy = startedBy; }
    }
    private static final class StepState {
        final StepDefinition definition; String status = "PENDING"; int attemptCount; Instant startedAt;
        Instant completedAt; String errorCode; String message; Map<String, Object> output = Map.of();
        StepState(StepDefinition definition) { this.definition = definition; }
    }
    private static final class ExceptionState {
        final String id = UUID.randomUUID().toString(); final String stepCode; final String errorCode;
        final Map<String, Object> details; String status = "OPEN"; String resolution; String resolvedBy; Instant resolvedAt;
        ExceptionState(String stepCode, String errorCode, String message, Map<String, Object> details) {
            this.stepCode = stepCode; this.errorCode = errorCode;
            Map<String, Object> values = new LinkedHashMap<>(details); values.put("message", Objects.toString(message, ""));
            this.details = Collections.unmodifiableMap(values);
        }
    }
}
