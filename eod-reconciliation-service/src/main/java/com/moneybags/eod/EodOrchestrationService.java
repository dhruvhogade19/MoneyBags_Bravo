package com.moneybags.eod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.eod.EodController.BusinessDateResponse;
import com.moneybags.eod.EodController.EodExceptionResolutionRequest;
import com.moneybags.eod.EodController.EodRunResponse;
import com.moneybags.eod.EodController.ExceptionResponse;
import com.moneybags.eod.EodController.OpenBusinessDateRequest;
import com.moneybags.eod.EodController.StartEodRunRequest;
import com.moneybags.eod.EodController.StepResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
class EodOrchestrationService {
    static final List<StepDefinition> STEPS = List.of(
            new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/open"),
            new StepDefinition("PAYMENTS_CUTOFF", 2, "payments-service", "POST", "/api/v1/payments/operations/eod/cutoff"),
            new StepDefinition("PAYMENTS_DRAIN", 3, "payments-service", "POST", "/api/v1/payments/operations/eod/drain"),
            new StepDefinition("CREDIT_CARD_READINESS", 4, "credit-card-service", "GET", "/api/credit-cards/accounts/eod/readiness"),
            new StepDefinition("DEPOSIT_READINESS", 5, "deposit-account-service", "GET", "/api/deposit-accounts/operations/eod/readiness"),
            new StepDefinition("DEPOSIT_ACCRUALS", 6, "deposit-account-service", "POST", "/api/deposit-accounts/operations/eod/account-accruals"),
            new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST", "/api/deposit-accounts/operations/eod/fixed-deposit-accruals"),
            new StepDefinition("FIXED_DEPOSIT_MATURITIES", 8, "deposit-account-service", "POST", "/api/deposit-accounts/operations/eod/fixed-deposit-maturities"),
            new StepDefinition("BILLS_CLOSE", 9, "bill-generation-service", "POST", "/internal/v1/bills/eod/close"),
            new StepDefinition("TRIAL_BALANCE", 10, "accounting-service", "POST", "/internal/v1/trial-balances"),
            new StepDefinition("PAYMENTS_RECONCILIATION", 11, "accounting-service", "POST", "/internal/v1/eod/reconciliation/runs"),
            new StepDefinition("FIXED_DEPOSIT_RECONCILIATION", 12, "accounting-service", "POST", "/internal/v1/eod/reconciliation/runs"),
            new StepDefinition("ACCOUNTING_PERIOD_CLOSE", 13, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/close"),
            new StepDefinition("ACCOUNTING_PERIOD_OPEN_NEXT", 14, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/open"),
            new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/api/v1/payments/operations/eod/reopen")
    );

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PeerOperations peers;
    private final EodBusinessDateRepository businessDates;
    private final EodRunRepository runs;
    private final EodExceptionRepository exceptions;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final LocalDate initialBusinessDate;
    private final String currency;
    private final TaskExecutor executor;
    private final Set<String> activeRuns = ConcurrentHashMap.newKeySet();

    EodOrchestrationService(PeerOperations peers,
                            EodBusinessDateRepository businessDates,
                            EodRunRepository runs,
                            EodExceptionRepository exceptions,
                            ObjectMapper json,
                            PlatformTransactionManager transactionManager,
                            @Qualifier("eodTaskExecutor") TaskExecutor executor,
                            @Value("${moneybags.eod.initial-business-date:2026-08-13}") LocalDate initialDate,
                            @Value("${moneybags.eod.currency:INR}") String currency) {
        this.peers = peers;
        this.businessDates = businessDates;
        this.runs = runs;
        this.exceptions = exceptions;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.initialBusinessDate = initialDate;
        this.currency = currency;
    }

    BusinessDateResponse businessDate() {
        return inTransaction(() -> businessDateResponse(currentBusinessDate(false)));
    }

    EodRunResponse start(String key, StartEodRunRequest request) {
        return start(key, request, null);
    }

    EodRunResponse start(String key, StartEodRunRequest request, String operatorAuthorization) {
        StartDecision decision = inTransaction(() -> {
            var existing = runs.findByIdempotencyKey(key);
            if (existing.isPresent()) return new StartDecision(existing.get().id(), true);

            EodBusinessDateEntity current = currentBusinessDate(true);
            LocalDate requestedDate = request.businessDate() == null ? current.businessDate() : request.businessDate();
            if (!requestedDate.equals(current.businessDate()) || !"OPEN".equals(current.status())) {
                throw new EodConflictException("Business date is not open for EOD processing: " + requestedDate);
            }

            EodRunEntity run = new EodRunEntity(UUID.randomUUID().toString(), key, requestedDate,
                    request.startedBy(), STEPS);
            current.startEod();
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return new StartDecision(run.id(), false);
        });

        if (!decision.existing()) schedule(decision.runId(), 0, operatorAuthorization);
        return get(decision.runId());
    }

    EodRunResponse get(String runId) {
        return inTransaction(() -> response(requireRun(runId)));
    }

    List<EodRunResponse> list(LocalDate businessDate) {
        return inTransaction(() -> (businessDate == null
                ? runs.findTop50ByOrderByStartedAtDesc()
                : runs.findAllByBusinessDateOrderByStartedAtDesc(businessDate))
                .stream().map(this::response).toList());
    }

    EodRunResponse resume(String runId) { return resume(runId, null); }

    EodRunResponse resume(String runId, String operatorAuthorization) {
        int firstIncomplete = inTransaction(() -> firstIncomplete(requireRun(runId)));
        if (firstIncomplete < STEPS.size()) schedule(runId, firstIncomplete, operatorAuthorization);
        return get(runId);
    }

    EodRunResponse retry(String runId, String stepCode) { return retry(runId, stepCode, null); }

    EodRunResponse retry(String runId, String stepCode, String operatorAuthorization) {
        int index = indexOf(stepCode);
        if (index < 0) throw new EodNotFoundException("EOD step not found: " + stepCode);
        inTransaction(() -> {
            requireRun(runId).requireStep(stepCode);
            return null;
        });
        schedule(runId, index, operatorAuthorization);
        return get(runId);
    }

    EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request) {
        return inTransaction(() -> {
            EodExceptionEntity exception = exceptions.findById(exceptionId)
                    .orElseThrow(() -> new EodNotFoundException("EOD exception not found: " + exceptionId));
            exception.resolve(request.resolution(), request.resolvedBy(), request.waived());
            exceptions.saveAndFlush(exception);
            return response(exception.run());
        });
    }

    BusinessDateResponse openNext(OpenBusinessDateRequest request) {
        return inTransaction(() -> {
            EodBusinessDateEntity current = currentBusinessDate(true);
            LocalDate next = request.businessDate() == null
                    ? current.businessDate().plusDays(1) : request.businessDate();
            if (!next.isAfter(current.businessDate())) {
                throw new EodConflictException("The next business date must be after the current date");
            }
            current.advanceTo(next);
            businessDates.saveAndFlush(current);
            return businessDateResponse(current);
        });
    }

    private void schedule(String runId, int start, String operatorAuthorization) {
        if (!activeRuns.add(runId)) return;
        executor.execute(() -> {
            try {
                executeFrom(runId, start, operatorAuthorization);
            } finally {
                activeRuns.remove(runId);
            }
        });
    }

    private void executeFrom(String runId, int start, String operatorAuthorization) {
        for (int i = start; i < STEPS.size(); i++) {
            StepDefinition definition = STEPS.get(i);
            StepExecution execution = inTransaction(() -> prepareStep(runId, definition, operatorAuthorization));
            if (execution == null) continue;

            Map<String, Object> output;
            try {
                output = peers.execute(definition, execution.context(), execution.outputs());
            } catch (PeerOperationException exception) {
                fail(runId, definition.code(), exception.code(), exception.getMessage(), exception.details());
                return;
            } catch (RuntimeException exception) {
                fail(runId, definition.code(), "UPSTREAM_ERROR", exception.getMessage(), Map.of());
                return;
            }

            Map<String, Object> storedOutput = output == null ? Map.of() : output;
            inTransaction(() -> {
                EodRunEntity run = requireRun(runId);
                EodRunStepEntity step = run.requireStep(definition.code());
                step.markCompleted(writeJson(storedOutput));
                run.exceptions().stream()
                        .filter(value -> value.stepCode().equals(definition.code()) && "OPEN".equals(value.status()))
                        .forEach(EodExceptionEntity::resolveAfterRetry);
                runs.saveAndFlush(run);
                return null;
            });
        }

        inTransaction(() -> {
            EodRunEntity run = requireRun(runId);
            if (run.steps().stream().allMatch(step -> "COMPLETED".equals(step.status()))) {
                run.markCompleted();
                EodBusinessDateEntity current = currentBusinessDate(true);
                if (current.businessDate().equals(run.businessDate())) {
                    current.advanceTo(run.businessDate().plusDays(1));
                    businessDates.saveAndFlush(current);
                }
                runs.saveAndFlush(run);
            }
            return null;
        });
    }

    private StepExecution prepareStep(String runId, StepDefinition definition, String operatorAuthorization) {
        EodRunEntity run = requireRun(runId);
        EodRunStepEntity step = run.requireStep(definition.code());
        if ("COMPLETED".equals(step.status())) return null;

        run.markRunning();
        step.markRunning();
        EodBusinessDateEntity current = currentBusinessDate(true);
        if (current.businessDate().equals(run.businessDate())) current.startEod();
        runs.saveAndFlush(run);
        businessDates.saveAndFlush(current);
        return new StepExecution(new EodContext(run.id(), run.businessDate(), run.startedBy(), currency,
                operatorAuthorization),
                completedOutputs(run));
    }

    private void fail(String runId, String stepCode, String errorCode, String message,
                      Map<String, Object> details) {
        inTransaction(() -> {
            EodRunEntity run = requireRun(runId);
            Map<String, Object> storedDetails = new LinkedHashMap<>(details == null ? Map.of() : details);
            storedDetails.put("message", Objects.toString(message, ""));
            run.markFailed(run.requireStep(stepCode), errorCode, message, writeJson(storedDetails));
            EodBusinessDateEntity current = currentBusinessDate(true);
            if (current.businessDate().equals(run.businessDate())) current.markFailed();
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return null;
        });
    }

    private Map<String, Map<String, Object>> completedOutputs(EodRunEntity run) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        run.steps().stream().filter(step -> "COMPLETED".equals(step.status()))
                .forEach(step -> result.put(step.code(), readJson(step.outputJson())));
        return result;
    }

    private int firstIncomplete(EodRunEntity run) {
        for (int i = 0; i < run.steps().size(); i++) {
            if (!"COMPLETED".equals(run.steps().get(i).status())) return i;
        }
        return run.steps().size();
    }

    private int indexOf(String stepCode) {
        for (int i = 0; i < STEPS.size(); i++) {
            if (STEPS.get(i).code().equalsIgnoreCase(stepCode)) return i;
        }
        return -1;
    }

    private EodBusinessDateEntity currentBusinessDate(boolean lock) {
        var current = lock
                ? businessDates.findForUpdate(EodBusinessDateEntity.CURRENT_RECORD_ID)
                : businessDates.findById(EodBusinessDateEntity.CURRENT_RECORD_ID);
        return current.orElseGet(() -> businessDates.saveAndFlush(new EodBusinessDateEntity(initialBusinessDate)));
    }

    private EodRunEntity requireRun(String runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new EodNotFoundException("EOD run not found: " + runId));
    }

    private BusinessDateResponse businessDateResponse(EodBusinessDateEntity value) {
        return new BusinessDateResponse(value.businessDate(), value.status(), instant(value.cutoffAt()),
                instant(value.openedAt()), instant(value.closedAt()), value.version());
    }

    private EodRunResponse response(EodRunEntity run) {
        return new EodRunResponse(run.id(), run.businessDate(), run.status(), run.startedBy(),
                instant(run.startedAt()), instant(run.completedAt()),
                run.steps().stream().map(step -> new StepResponse(step.code(), step.sequence(),
                        step.providerService(), step.method(), step.path(), step.status(), step.commandReference(),
                        step.attemptCount(), instant(step.startedAt()), instant(step.completedAt()),
                        step.errorCode(), step.message(), readJson(step.outputJson()))).toList(),
                run.exceptions().stream().map(value -> new ExceptionResponse(value.id(), value.stepCode(),
                        value.severity(), value.errorCode(), readJson(value.detailsJson()), value.status(),
                        value.resolution(), value.resolvedBy(), instant(value.resolvedAt()))).toList(),
                run.apiVersion());
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("EOD state could not be serialized", exception);
        }
    }

    private Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored EOD state is invalid", exception);
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record StartDecision(String runId, boolean existing) {}
    private record StepExecution(EodContext context, Map<String, Map<String, Object>> outputs) {}
}
