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
import java.util.function.Supplier;

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

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PeerOperations peers;
    private final EodBusinessDateRepository businessDates;
    private final EodRunRepository runs;
    private final EodExceptionRepository exceptions;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final LocalDate initialBusinessDate;
    private final String currency;

    EodOrchestrationService(PeerOperations peers,
                            EodBusinessDateRepository businessDates,
                            EodRunRepository runs,
                            EodExceptionRepository exceptions,
                            ObjectMapper json,
                            PlatformTransactionManager transactionManager,
                            @Value("${moneybags.eod.initial-business-date:2026-08-13}") LocalDate initialDate,
                            @Value("${moneybags.eod.currency:INR}") String currency) {
        this.peers = peers;
        this.businessDates = businessDates;
        this.runs = runs;
        this.exceptions = exceptions;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.initialBusinessDate = initialDate;
        this.currency = currency;
    }

    synchronized BusinessDateResponse businessDate() {
        return inTransaction(() -> businessDateResponse(currentBusinessDate(false)));
    }

    synchronized EodRunResponse start(String key, StartEodRunRequest request) {
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

        if (!decision.existing()) executeFrom(decision.runId(), 0);
        return get(decision.runId());
    }

    synchronized EodRunResponse get(String runId) {
        return inTransaction(() -> response(requireRun(runId)));
    }

    synchronized EodRunResponse resume(String runId) {
        int firstIncomplete = inTransaction(() -> firstIncomplete(requireRun(runId)));
        if (firstIncomplete < STEPS.size()) executeFrom(runId, firstIncomplete);
        return get(runId);
    }

    synchronized EodRunResponse retry(String runId, String stepCode) {
        int index = indexOf(stepCode);
        if (index < 0) throw new EodNotFoundException("EOD step not found: " + stepCode);
        inTransaction(() -> {
            requireRun(runId).requireStep(stepCode);
            return null;
        });
        executeFrom(runId, index);
        return get(runId);
    }

    synchronized EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request) {
        return inTransaction(() -> {
            EodExceptionEntity exception = exceptions.findById(exceptionId)
                    .orElseThrow(() -> new EodNotFoundException("EOD exception not found: " + exceptionId));
            exception.resolve(request.resolution(), request.resolvedBy(), request.waived());
            exceptions.saveAndFlush(exception);
            return response(exception.run());
        });
    }

    synchronized BusinessDateResponse openNext(OpenBusinessDateRequest request) {
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

    private void executeFrom(String runId, int start) {
        for (int i = start; i < STEPS.size(); i++) {
            StepDefinition definition = STEPS.get(i);
            StepExecution execution = inTransaction(() -> prepareStep(runId, definition));
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

    private StepExecution prepareStep(String runId, StepDefinition definition) {
        EodRunEntity run = requireRun(runId);
        EodRunStepEntity step = run.requireStep(definition.code());
        if ("COMPLETED".equals(step.status())) return null;

        run.markRunning();
        step.markRunning();
        EodBusinessDateEntity current = currentBusinessDate(true);
        if (current.businessDate().equals(run.businessDate())) current.startEod();
        runs.saveAndFlush(run);
        businessDates.saveAndFlush(current);
        return new StepExecution(new EodContext(run.id(), run.businessDate(), run.startedBy(), currency),
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
