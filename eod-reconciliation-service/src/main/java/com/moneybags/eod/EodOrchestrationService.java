package com.moneybags.eod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.eod.EodController.BusinessDateResponse;
import com.moneybags.eod.EodController.EodExceptionResolutionRequest;
import com.moneybags.eod.EodController.EodResumeRequest;
import com.moneybags.eod.EodController.EodRunResponse;
import com.moneybags.eod.EodController.EodStepRetryRequest;
import com.moneybags.eod.EodController.ExceptionResponse;
import com.moneybags.eod.EodController.OpenBusinessDateRequest;
import com.moneybags.eod.EodController.StartEodRunRequest;
import com.moneybags.eod.EodController.StepResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
class EodOrchestrationService {
    private static final Logger LOG = LoggerFactory.getLogger(EodOrchestrationService.class);
    /** Kept as a compatibility hook for existing tests and operational tooling. */
    static final List<StepDefinition> STEPS = new EodWorkflowRegistry().currentSteps();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> PAYMENT_BARRIER = Set.of(
            "PAYMENTS_CUTOFF", "PAYMENTS_DRAIN", "PAYMENTS_REOPEN");
    private static final Set<String> OPERATIONAL_BARRIER = Set.of(
            "ACCOUNTING_PERIOD_OPEN_CURRENT", "PAYMENTS_CUTOFF", "PAYMENTS_DRAIN", "PAYMENTS_REOPEN");
    private static final Set<String> FINANCIAL_CLOSURE = Set.of(
            "TRIAL_BALANCE", "PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION",
            "ACCOUNTING_PERIOD_CLOSE", "ACCOUNTING_PERIOD_OPEN_NEXT");
    private static final List<String> RECOVERABLE_RUN_STATUSES = List.of("PENDING", "RUNNING", "FAILED");

    private final PeerOperations peers;
    private final EodWorkflowRegistry workflowRegistry;
    private final EodFailurePolicy failurePolicy;
    private final DemoStepPolicy demoStepPolicy;
    private final EodBusinessDateRepository businessDates;
    private final EodRunRepository runs;
    private final EodExceptionRepository exceptions;
    private final EodRunActionRepository actions;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final LocalDate initialBusinessDate;
    private final String currency;
    private final TaskExecutor executor;
    private final long leaseSeconds;
    private final Set<String> activeRuns = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingReschedules = ConcurrentHashMap.newKeySet();

    EodOrchestrationService(PeerOperations peers,
                            EodWorkflowRegistry workflowRegistry,
                            EodFailurePolicy failurePolicy,
                            DemoStepPolicy demoStepPolicy,
                            EodBusinessDateRepository businessDates,
                            EodRunRepository runs,
                            EodExceptionRepository exceptions,
                            EodRunActionRepository actions,
                            ObjectMapper json,
                            PlatformTransactionManager transactionManager,
                            @Qualifier("eodTaskExecutor") TaskExecutor executor,
                            @Value("${moneybags.eod.initial-business-date:2026-08-13}") LocalDate initialDate,
                            @Value("${moneybags.eod.currency:INR}") String currency,
                            @Value("${moneybags.eod.execution-lease-seconds:120}") long leaseSeconds) {
        this.peers = peers;
        this.workflowRegistry = workflowRegistry;
        this.failurePolicy = failurePolicy;
        this.demoStepPolicy = demoStepPolicy;
        this.businessDates = businessDates;
        this.runs = runs;
        this.exceptions = exceptions;
        this.actions = actions;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.initialBusinessDate = initialDate;
        this.currency = currency;
        if (leaseSeconds < 10) throw new IllegalArgumentException("EOD execution lease must be at least 10 seconds");
        this.leaseSeconds = leaseSeconds;
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
            existing = runs.findByIdempotencyKey(key);
            if (existing.isPresent()) return new StartDecision(existing.get().id(), true);

            LocalDate requestedDate = request.businessDate() == null ? current.businessDate() : request.businessDate();
            if (!requestedDate.equals(current.businessDate()) || !"OPEN".equals(current.status())) {
                throw new EodConflictException("Business date is not open for EOD processing: " + requestedDate);
            }

            EodRunEntity run = new EodRunEntity(UUID.randomUUID().toString(), key, requestedDate,
                    request.startedBy(), workflowRegistry.currentVersion(), workflowRegistry.currentSteps());
            current.startEod();
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            audit(run.id(), "START", null, request.startedBy(), null,
                    Map.of("workflowVersion", workflowRegistry.currentVersion(), "idempotencyKey", key));
            return new StartDecision(run.id(), false);
        });

        if (!decision.existing()) schedule(decision.runId(), operatorAuthorization);
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

    EodRunResponse resume(String runId) {
        return resume(runId, new EodResumeRequest("SYSTEM", "Programmatic resume"), null);
    }

    EodRunResponse resume(String runId, String operatorAuthorization) {
        return resume(runId, new EodResumeRequest("SYSTEM", "Programmatic resume"), operatorAuthorization);
    }

    EodRunResponse resume(String runId, EodResumeRequest request, String operatorAuthorization) {
        return resume(runId, request, operatorAuthorization, null);
    }

    EodRunResponse resume(String runId, EodResumeRequest request, String operatorAuthorization,
                          String requestKey) {
        boolean shouldSchedule = inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("runId", run.id());
            requestPayload.put("requestedBy", request.requestedBy());
            requestPayload.put("reason", request.reason());
            if (!recordActionRequest(run.id(), "RESUME", "RESUME", null, requestKey,
                    request.requestedBy(), request.reason(), requestPayload)) return false;
            if ("COMPLETED".equals(run.status())) return false;
            // The request itself is durable in EOD_RUN_ACTION. If another node owns a lease, that
            // node (or the recovery scanner) consumes the request after the active call finishes.
            if (hasActiveLease(run)) return true;
            applyManualContinuation(run, true);
            return hasIncompleteMainStep(run) || hasIncompleteFinalizer(run);
        });
        if (shouldSchedule) schedule(runId, operatorAuthorization);
        return get(runId);
    }

    EodRunResponse retry(String runId, String stepCode) {
        return retry(runId, stepCode, new EodStepRetryRequest("SYSTEM", "Programmatic retry"), null);
    }

    EodRunResponse retry(String runId, String stepCode, String operatorAuthorization) {
        return retry(runId, stepCode, new EodStepRetryRequest("SYSTEM", "Programmatic retry"),
                operatorAuthorization);
    }

    EodRunResponse retry(String runId, String stepCode, EodStepRetryRequest request,
                         String operatorAuthorization) {
        return retry(runId, stepCode, request, operatorAuthorization, null);
    }

    EodRunResponse retry(String runId, String stepCode, EodStepRetryRequest request,
                         String operatorAuthorization, String requestKey) {
        boolean shouldSchedule = inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            EodRunStepEntity step = run.requireStep(stepCode);
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("runId", run.id());
            requestPayload.put("stepCode", step.code());
            requestPayload.put("requestedBy", request.requestedBy());
            requestPayload.put("reason", request.reason());
            if (!recordActionRequest(run.id(), "RETRY", "STEP_RETRY", step.code(), requestKey,
                    request.requestedBy(), request.reason(), requestPayload)) return false;
            if (!"FAILED".equals(step.status())) {
                throw new EodConflictException("Only a failed EOD step can be retried: " + step.code());
            }
            if (hasActiveLease(run)) {
                throw new EodConflictException("The EOD run already has an active step execution");
            }
            applyManualContinuation(run, false);
            return true;
        });
        if (shouldSchedule) schedule(runId, operatorAuthorization);
        return get(runId);
    }

    EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request) {
        return resolve(exceptionId, request, null);
    }

    EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request, String requestKey) {
        return inTransaction(() -> {
            EodExceptionEntity exception = exceptions.findById(exceptionId)
                    .orElseThrow(() -> new EodNotFoundException("EOD exception not found: " + exceptionId));
            EodRunEntity run = requireRunForUpdate(exception.run().id());
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("runId", run.id());
            requestPayload.put("exceptionId", exception.id());
            requestPayload.put("resolution", request.resolution());
            requestPayload.put("resolvedBy", request.resolvedBy());
            requestPayload.put("waived", request.waived());
            String actionType = request.waived() ? "WAIVE_EXCEPTION" : "RESOLVE_EXCEPTION";
            if (!recordActionRequest(run.id(), actionType, "EXCEPTION_RESOLUTION", exception.stepCode(),
                    requestKey, request.resolvedBy(), request.resolution(), requestPayload)) {
                return response(run);
            }
            exception.resolve(request.resolution(), request.resolvedBy(), request.waived());
            exceptions.saveAndFlush(exception);
            return response(run);
        });
    }

    BusinessDateResponse openNext(OpenBusinessDateRequest request) {
        return inTransaction(() -> {
            EodBusinessDateEntity current = currentBusinessDate(true);
            LocalDate next = request.businessDate() == null ? current.businessDate() : request.businessDate();
            if (next.equals(current.businessDate()) && "OPEN".equals(current.status())) {
                return businessDateResponse(current);
            }
            if (!next.equals(current.businessDate().plusDays(1))) {
                throw new EodConflictException("The next business date must be exactly "
                        + current.businessDate().plusDays(1));
            }
            boolean priorDateCompleted = runs.findAllByBusinessDateOrderByStartedAtDesc(current.businessDate())
                    .stream().anyMatch(this::fullyCompletedWithFinalizer);
            if (!priorDateCompleted) {
                throw new EodConflictException("The current business date cannot advance until its EOD run "
                        + "and PAYMENTS_REOPEN finalizer are completed");
            }
            current.advanceTo(next);
            businessDates.saveAndFlush(current);
            return businessDateResponse(current);
        });
    }

    /** Invoked at startup and periodically so an expired JVM lease cannot strand a cutoff run. */
    void recoverInterruptedRuns() {
        List<String> candidates = inTransaction(() -> runs.findIdsByStatusIn(RECOVERABLE_RUN_STATUSES));
        for (String runId : candidates) {
            boolean recover = inTransaction(() -> prepareRecovery(runId));
            if (recover) schedule(runId, null);
        }
    }

    private void schedule(String runId, String operatorAuthorization) {
        if (!activeRuns.add(runId)) {
            pendingReschedules.add(runId);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    executeRun(runId, operatorAuthorization);
                } finally {
                    activeRuns.remove(runId);
                    if (pendingReschedules.remove(runId)) schedule(runId, operatorAuthorization);
                }
            });
        } catch (RuntimeException exception) {
            activeRuns.remove(runId);
            failScheduling(runId, exception);
        }
    }

    private void executeRun(String runId, String operatorAuthorization) {
        String workerToken = UUID.randomUUID().toString();
        boolean shouldExecute;
        try {
            shouldExecute = inTransaction(() -> {
                EodRunEntity run = requireRunForUpdate(runId);
                if ("COMPLETED".equals(run.status())) return false;
                if (!validPaymentFinalizer(run)) {
                    failInvalidFinalizerSnapshot(run);
                    return false;
                }
                if (!hasActiveLease(run)) consumePendingManualContinuation(run);
                rearmOperationalBarrier(run, false);
                return true;
            });
        } catch (RuntimeException exception) {
            bestEffortUnexpectedFailure(runId, null, workerToken, exception);
            shouldExecute = true; // Still make a best-effort attempt to run the safety finalizer.
        }
        if (!shouldExecute) return;

        boolean mainFailed = safelyHasFailedMainStep(runId);
        String activeStepCode = null;
        try {
            while (!mainFailed) {
                ClaimDecision decision = inTransaction(() -> claimNextMainStep(runId, workerToken,
                        operatorAuthorization));
                if (decision.state() == ClaimState.BUSY) return;
                if (decision.state() == ClaimState.NONE) break;
                if (decision.state() == ClaimState.DEADLOCK) {
                    failDependencyGraph(runId, decision.blockedStepCode());
                    mainFailed = true;
                    break;
                }
                activeStepCode = decision.claim().stepCode();
                if (!executeClaim(decision.claim())) {
                    mainFailed = true;
                    break;
                }
                activeStepCode = null;
            }
        } catch (RuntimeException exception) {
            mainFailed = true;
            bestEffortUnexpectedFailure(runId, activeStepCode, workerToken, exception);
        }

        if (!mainFailed) {
            try {
                if (!prepareRolloverBeforePaymentRelease(runId)) return;
            } catch (RuntimeException exception) {
                mainFailed = true;
                bestEffortUnexpectedFailure(runId, "PAYMENTS_REOPEN", workerToken, exception);
            }
        }
        if (mainFailed) {
            if (!safelyHasFailedMainStep(runId)) return;
            try {
                if (holdPaymentsForFinancialRecovery(runId, workerToken)) return;
            } catch (RuntimeException exception) {
                // Failing closed is safer than attempting the remote release after an unknown DB state.
                LOG.error("Unable to persist the payment recovery hold for EOD run {}", runId, exception);
                return;
            }
        }

        boolean finalizerCompleted = false;
        try {
            ClaimDecision finalizer = inTransaction(() -> claimFinalizer(runId, workerToken,
                    operatorAuthorization));
            if (finalizer.state() == ClaimState.BUSY) return;
            if (finalizer.state() == ClaimState.INVALID_FINALIZER) return;
            if (finalizer.state() == ClaimState.DEADLOCK) {
                failDependencyGraph(runId, finalizer.blockedStepCode());
                return;
            }
            finalizerCompleted = finalizer.state() == ClaimState.NONE || executeClaim(finalizer.claim());
        } catch (RuntimeException exception) {
            bestEffortUnexpectedFailure(runId, "PAYMENTS_REOPEN", workerToken, exception);
        }
        if (!mainFailed && finalizerCompleted) completeRun(runId);
        if (finalizerCompleted && safelyHasPendingManualContinuation(runId)) {
            pendingReschedules.add(runId);
        }
    }

    private boolean executeClaim(StepClaim claim) {
        StepDefinition definition = claim.definition();
        if (demoStepPolicy.skips(definition.code())) {
            completePolicySkip(claim, demoStepPolicy.output(definition.code(), claim.context()),
                    demoStepPolicy.reason(definition.code()), "SYSTEM_DEMO_POLICY",
                    demoStepPolicy.allStepsEnabled() ? "DEMO_ALL_STEPS_BYPASS" : "DEMO_SKIP");
            return true;
        }

        int activationAttempt = 1;
        while (true) {
            Map<String, Object> output;
            try {
                output = peers.execute(definition, claim.context(), claim.outputs());
            } catch (RuntimeException exception) {
                FailureDescriptor failure = failurePolicy.describe(exception, definition, activationAttempt);
                if (failure.retryable()) {
                    try {
                        failurePolicy.backoff(definition, activationAttempt);
                    } catch (RuntimeException interrupted) {
                        FailureDescriptor interruptedFailure = failurePolicy.describe(interrupted, definition,
                                activationAttempt);
                        failStep(claim, interruptedFailure);
                        return false;
                    }
                    activationAttempt++;
                    recordAutomaticRetry(claim, failure, activationAttempt);
                    continue;
                }
                if (definition.optional()) {
                    completeOptionalSkip(claim, failure);
                    return true;
                }
                failStep(claim, failure);
                return false;
            }
            // Persistence/lease failures are orchestration failures, not upstream failures. Let the
            // outer safety boundary persist them and still attempt PAYMENTS_REOPEN.
            completeStep(claim, output == null ? Map.of() : output);
            return true;
        }
    }

    private ClaimDecision claimNextMainStep(String runId, String workerToken, String operatorAuthorization) {
        EodRunEntity run = requireRunForUpdate(runId);
        List<EodRunStepEntity> main = run.steps().stream().filter(step -> !step.definition().finalizer())
                .sorted(Comparator.comparingInt(EodRunStepEntity::sequence)).toList();
        if (main.stream().allMatch(step -> "COMPLETED".equals(step.status()))) return ClaimDecision.none();

        OffsetDateTime now = OffsetDateTime.now();
        if (main.stream().anyMatch(step -> step.hasActiveLease(now))) return ClaimDecision.busy();

        for (EodRunStepEntity step : main) {
            if ("COMPLETED".equals(step.status())) continue;
            if (!Set.of("PENDING", "RUNNING").contains(step.status())) continue;
            if (dependenciesCompleted(run, step)) {
                return ClaimDecision.claimed(claim(run, step, workerToken, operatorAuthorization, false));
            }
        }
        String blocked = main.stream().filter(step -> !"COMPLETED".equals(step.status()))
                .map(EodRunStepEntity::code).findFirst().orElse("WORKFLOW");
        return ClaimDecision.deadlock(blocked);
    }

    private ClaimDecision claimFinalizer(String runId, String workerToken, String operatorAuthorization) {
        EodRunEntity run = requireRunForUpdate(runId);
        List<EodRunStepEntity> finalizers = run.steps().stream()
                .filter(step -> step.definition().finalizer()).toList();
        if (!validPaymentFinalizer(run)) {
            failInvalidFinalizerSnapshot(run);
            return ClaimDecision.invalidFinalizer();
        }
        EodRunStepEntity finalizer = finalizers.getFirst();
        if ("COMPLETED".equals(finalizer.status())) return ClaimDecision.none();
        if (finalizer.hasActiveLease(OffsetDateTime.now())) return ClaimDecision.busy();
        if (!Set.of("PENDING", "RUNNING").contains(finalizer.status()))
            return ClaimDecision.deadlock(finalizer.code());
        return ClaimDecision.claimed(claim(run, finalizer, workerToken, operatorAuthorization, true));
    }

    private StepClaim claim(EodRunEntity run, EodRunStepEntity step, String workerToken,
                            String operatorAuthorization, boolean finalizer) {
        if (!finalizer) run.markRunning();
        step.markRunning(workerToken, OffsetDateTime.now().plusSeconds(leaseSeconds));
        EodBusinessDateEntity current = currentBusinessDate(true);
        if (current.businessDate().equals(run.businessDate()) && !finalizer) current.startEod();
        runs.saveAndFlush(run);
        businessDates.saveAndFlush(current);
        EodContext context = new EodContext(run.id(), run.businessDate(), run.startedBy(), currency,
                operatorAuthorization, step.executionEpoch(), step.commandReference());
        return new StepClaim(run.id(), step.code(), step.definition(), workerToken, context,
                completedOutputs(run));
    }

    private void recordAutomaticRetry(StepClaim claim, FailureDescriptor failure, int activationAttempt) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(claim.runId());
            EodRunStepEntity step = run.requireStep(claim.stepCode());
            requireLease(step, claim.workerToken());
            step.markAutomaticRetry(claim.workerToken(), OffsetDateTime.now().plusSeconds(leaseSeconds),
                    failure.code(), failure.message(), failure.classification());
            audit(run.id(), "AUTO_RETRY", step.code(), "SYSTEM", failure.message(),
                    Map.of("activationAttempt", activationAttempt, "failureClass", failure.classification().name(),
                            "errorCode", failure.code(), "executionEpoch", step.executionEpoch()));
            runs.saveAndFlush(run);
            return null;
        });
    }

    private void completeStep(StepClaim claim, Map<String, Object> output) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(claim.runId());
            EodRunStepEntity step = run.requireStep(claim.stepCode());
            requireLease(step, claim.workerToken());
            step.markCompleted(writeJson(output));
            run.exceptions().stream()
                    .filter(value -> value.stepCode().equals(step.code()) && "OPEN".equals(value.status()))
                    .forEach(EodExceptionEntity::resolveAfterRetry);
            runs.saveAndFlush(run);
            return null;
        });
    }

    private void completePolicySkip(StepClaim claim, Map<String, Object> output, String reason,
                                    String actor, String actionType) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(claim.runId());
            EodRunStepEntity step = run.requireStep(claim.stepCode());
            requireLease(step, claim.workerToken());
            step.markCompleted(writeJson(output));
            run.exceptions().stream()
                    .filter(value -> value.stepCode().equals(step.code()) && "OPEN".equals(value.status()))
                    .forEach(value -> value.resolve(reason, actor, true));
            audit(run.id(), actionType, step.code(), actor, reason,
                    Map.of("executionMode", step.executionMode().name(), "executionEpoch", step.executionEpoch()));
            runs.saveAndFlush(run);
            return null;
        });
    }

    private void completeOptionalSkip(StepClaim claim, FailureDescriptor failure) {
        Map<String, Object> output = new LinkedHashMap<>(failure.details());
        output.put("status", "SKIPPED");
        output.put("optional", true);
        output.put("controlBypassed", false);
        String reason = "Optional EOD step exhausted its retry policy: " + failure.code();
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(claim.runId());
            EodRunStepEntity step = run.requireStep(claim.stepCode());
            requireLease(step, claim.workerToken());
            step.markCompleted(writeJson(output));
            run.recordSkippedException(step, failure.code(), writeJson(failure.details()), reason,
                    "SYSTEM_OPTIONAL_POLICY");
            audit(run.id(), "OPTIONAL_SKIP", step.code(), "SYSTEM_OPTIONAL_POLICY", reason,
                    failure.details());
            runs.saveAndFlush(run);
            return null;
        });
    }

    private void failStep(StepClaim claim, FailureDescriptor failure) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(claim.runId());
            EodRunStepEntity step = run.requireStep(claim.stepCode());
            requireLease(step, claim.workerToken());
            run.markFailed(step, failure.code(), failure.message(), writeJson(failure.details()),
                    failure.classification());
            EodBusinessDateEntity current = currentBusinessDate(true);
            if (current.businessDate().equals(run.businessDate())) current.markFailed();
            audit(run.id(), "STEP_FAILED", step.code(), "SYSTEM", failure.message(), failure.details());
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return null;
        });
    }

    private void failDependencyGraph(String runId, String stepCode) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            EodRunStepEntity step = run.requireStep(stepCode);
            Map<String, Object> details = Map.of("failureClass", FailureClass.PERMANENT.name(),
                    "message", "No EOD step is runnable because persisted dependencies are unsatisfied",
                    "dependencies", step.dependencies(), "workflowVersion", run.workflowVersion());
            run.markFailed(step, "WORKFLOW_DEPENDENCY_BLOCKED", Objects.toString(details.get("message")),
                    writeJson(details), FailureClass.PERMANENT);
            EodBusinessDateEntity current = currentBusinessDate(true);
            if (current.businessDate().equals(run.businessDate())) current.markFailed();
            audit(run.id(), "WORKFLOW_BLOCKED", step.code(), "SYSTEM", Objects.toString(details.get("message")),
                    details);
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return null;
        });
    }

    private void failScheduling(String runId, RuntimeException exception) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            EodRunStepEntity step = run.steps().stream().filter(value -> !"COMPLETED".equals(value.status()))
                    .findFirst().orElse(null);
            if (step == null) return null;
            Map<String, Object> details = Map.of("failureClass", FailureClass.TRANSIENT.name(),
                    "message", Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
            run.markFailed(step, "EXECUTOR_UNAVAILABLE", Objects.toString(details.get("message")),
                    writeJson(details), FailureClass.TRANSIENT);
            EodBusinessDateEntity current = currentBusinessDate(true);
            if (current.businessDate().equals(run.businessDate())) current.markFailed();
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return null;
        });
    }

    private void completeRun(String runId) {
        inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            if (run.steps().stream().allMatch(step -> "COMPLETED".equals(step.status()))) {
                if (!validPaymentFinalizer(run)) {
                    failInvalidFinalizerSnapshot(run);
                    return null;
                }
                EodBusinessDateEntity current = currentBusinessDate(true);
                finalizeCompletedRun(run, current, "COMPLETE");
            }
            return null;
        });
    }

    private boolean prepareRolloverBeforePaymentRelease(String runId) {
        return inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            if (!validPaymentFinalizer(run)) {
                failInvalidFinalizerSnapshot(run);
                return false;
            }
            boolean mainCompleted = run.steps().stream().filter(step -> !step.definition().finalizer())
                    .allMatch(step -> "COMPLETED".equals(step.status()));
            if (!mainCompleted) throw new EodConflictException("EOD rollover is not ready for payment release");

            EodBusinessDateEntity current = currentBusinessDate(true);
            LocalDate next = run.businessDate().plusDays(1);
            if (current.businessDate().equals(run.businessDate())) {
                current.prepareNextDate(next);
                businessDates.saveAndFlush(current);
                audit(run.id(), "ROLLOVER_READY", "PAYMENTS_REOPEN", "SYSTEM",
                        "Local business date prepared before releasing the remote payment fence",
                        Map.of("businessDate", next, "status", current.status()));
            } else if (!current.businessDate().equals(next)) {
                throw new EodConflictException("Local business date diverged while finalizing EOD run " + run.id());
            }
            return true;
        });
    }

    private void finalizeCompletedRun(EodRunEntity run, EodBusinessDateEntity current, String actionType) {
        if (!validPaymentFinalizer(run)) {
            failInvalidFinalizerSnapshot(run);
            return;
        }
        LocalDate next = run.businessDate().plusDays(1);
        if (current.businessDate().equals(run.businessDate())) {
            // Compatibility recovery for runs completed by the previous ordering.
            current.advanceTo(next);
        } else if (current.businessDate().equals(next) && !"OPEN".equals(current.status())) {
            current.openPreparedDate();
        } else if (!current.businessDate().equals(next)) {
            throw new EodConflictException("Cannot finalize EOD run because the business date has diverged");
        }
        run.markCompleted();
        businessDates.saveAndFlush(current);
        runs.saveAndFlush(run);
        audit(run.id(), actionType, null, "SYSTEM", null,
                Map.of("workflowVersion", run.workflowVersion(), "businessDate", next));
    }

    private boolean paymentFenceProven(EodRunEntity run) {
        try {
            EodRunStepEntity cutoff = run.requireStep("PAYMENTS_CUTOFF");
            EodRunStepEntity drain = run.requireStep("PAYMENTS_DRAIN");
            if (!"COMPLETED".equals(cutoff.status()) || !"COMPLETED".equals(drain.status())) return false;
            Map<String, Object> cutoffOutput = readJson(cutoff.outputJson());
            Map<String, Object> drainOutput = readJson(drain.outputJson());
            String cutoffOwner = Objects.toString(cutoffOutput.get("commandReference"), "");
            String drainOwner = Objects.toString(drainOutput.get("commandReference"), "");
            return !cutoffOwner.isBlank() && cutoffOwner.equals(drainOwner)
                    && "CUT_OFF".equals(Objects.toString(cutoffOutput.get("status"), ""))
                    && "DRAINED".equals(Objects.toString(drainOutput.get("status"), ""))
                    && !Boolean.parseBoolean(Objects.toString(cutoffOutput.get("newPaymentIntake"), "true"))
                    && !Boolean.parseBoolean(Objects.toString(drainOutput.get("newPaymentIntake"), "true"))
                    && run.businessDate().toString().equals(Objects.toString(drainOutput.get("businessDate"), ""))
                    && currency.equalsIgnoreCase(Objects.toString(drainOutput.get("currencyCode"), ""));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void markRecoveryFenceRequired(EodRunEntity run, String cause) {
        EodRunStepEntity finalizer = run.steps().stream()
                .filter(step -> "PAYMENTS_REOPEN".equals(step.code())).findFirst().orElse(null);
        if (finalizer == null || "COMPLETED".equals(finalizer.status())) return;
        if ("FAILED".equals(finalizer.status())
                && "RECOVERY_FENCE_REQUIRED".equals(finalizer.errorCode())) return;
        finalizer.markRunning("RECOVERY-SCANNER", OffsetDateTime.now().plusSeconds(leaseSeconds));
        String message = "Payment intake state could not be proven closed; operator resume must "
                + "re-establish cutoff and drain";
        Map<String, Object> details = Map.of(
                "failureClass", FailureClass.BUSINESS.name(),
                "paymentsIntake", "UNKNOWN",
                "cause", cause,
                "workflowVersion", run.workflowVersion());
        run.markFailed(finalizer, "RECOVERY_FENCE_REQUIRED", message, writeJson(details),
                FailureClass.BUSINESS);
        audit(run.id(), "RECOVERY_FENCE_UNPROVEN", finalizer.code(), "SYSTEM_RECOVERY", message, details);
        runs.saveAndFlush(run);
    }

    private void applyManualContinuation(EodRunEntity run, boolean consumesResumeRequest) {
        boolean heldBehindFence = run.steps().stream()
                .filter(step -> "PAYMENTS_REOPEN".equals(step.code()))
                .anyMatch(step -> "FAILED".equals(step.status())
                        && "HELD_FOR_EOD_RECOVERY".equals(step.errorCode()));
        if (!heldBehindFence) {
            rearmOperationalBarrier(run, true);
            invalidateFinancialClosure(run);
        } else {
            reassertHeldPaymentBarrier(run);
        }
        rearmFailedSteps(run);
        audit(run.id(), consumesResumeRequest ? "RESUME_APPLIED" : "RETRY_APPLIED", null, "SYSTEM",
                "Manual continuation state was persisted and is ready for dependency-safe execution",
                Map.of("workflowVersion", run.workflowVersion(), "paymentsHeldClosed", heldBehindFence));
        runs.saveAndFlush(run);
    }

    private void reassertHeldPaymentBarrier(EodRunEntity run) {
        List<String> reasserted = new ArrayList<>();
        for (EodRunStepEntity step : run.steps()) {
            if (Set.of("PAYMENTS_CUTOFF", "PAYMENTS_DRAIN").contains(step.code())) {
                // Keep the original epoch/owner: this is a state assertion, not a new business command.
                step.resetForResume(false);
                reasserted.add(step.code());
            }
        }
        if (reasserted.isEmpty()) return;
        runs.saveAndFlush(run);
        audit(run.id(), "REASSERT_HELD_PAYMENT_BARRIER", null, "SYSTEM",
                "Reasserting the existing owned cutoff before resuming financial work",
                Map.of("steps", reasserted));
    }

    private void consumePendingManualContinuation(EodRunEntity run) {
        long requested = actions.countByRunIdAndActionType(run.id(), "RESUME");
        long applied = actions.countByRunIdAndActionType(run.id(), "RESUME_APPLIED");
        if (requested > applied) applyManualContinuation(run, true);
    }

    private void rearmOperationalBarrier(EodRunEntity run, boolean manualContinuation) {
        EodRunStepEntity finalizer = run.steps().stream()
                .filter(step -> "PAYMENTS_REOPEN".equals(step.code()))
                .findFirst().orElse(null);
        if (finalizer == null || !hasIncompleteMainStep(run)) return;
        if (!manualContinuation && !"COMPLETED".equals(finalizer.status())) return;
        if (hasActiveLease(run)) return;

        List<String> rearmed = new ArrayList<>();
        int barrierEpoch = run.steps().stream().filter(step -> PAYMENT_BARRIER.contains(step.code()))
                .mapToInt(EodRunStepEntity::executionEpoch).max().orElse(0) + 1;
        for (EodRunStepEntity step : run.steps()) {
            if (OPERATIONAL_BARRIER.contains(step.code())) {
                if (PAYMENT_BARRIER.contains(step.code())) step.resetForResumeAtEpoch(barrierEpoch);
                else step.resetForResume(true);
                rearmed.add(step.code());
            }
        }
        if (rearmed.isEmpty()) return;
        runs.saveAndFlush(run);
        audit(run.id(), "REARM_PAYMENT_BARRIER", null, "SYSTEM",
                "A continuation always re-establishes payment cutoff and drain before financial processing",
                Map.of("steps", rearmed, "manualContinuation", manualContinuation));
    }

    private void invalidateFinancialClosure(EodRunEntity run) {
        if (!hasIncompleteMainStep(run) || hasActiveLease(run)) return;
        List<String> invalidated = new ArrayList<>();
        for (EodRunStepEntity step : run.steps()) {
            if (FINANCIAL_CLOSURE.contains(step.code()) && !"PENDING".equals(step.status())) {
                step.resetForResume(true);
                invalidated.add(step.code());
            }
        }
        if (invalidated.isEmpty()) return;
        runs.saveAndFlush(run);
        audit(run.id(), "INVALIDATE_FINANCIAL_CLOSURE", null, "SYSTEM",
                "Payment intake was reopened; date snapshots and closure controls require a fresh epoch",
                Map.of("steps", invalidated));
    }

    private void rearmFailedSteps(EodRunEntity run) {
        List<String> rearmed = new ArrayList<>();
        for (EodRunStepEntity step : run.steps()) {
            if ("FAILED".equals(step.status())) {
                step.resetForResume(true);
                rearmed.add(step.code());
            }
        }
        if (rearmed.isEmpty()) return;
        runs.saveAndFlush(run);
        audit(run.id(), "REARM_FAILED_STEPS", null, "SYSTEM",
                "Manual continuation uses a fresh idempotency epoch while preserving command references",
                Map.of("steps", rearmed));
    }

    private boolean prepareRecovery(String runId) {
        EodRunEntity run = requireRunForUpdate(runId);
        if ("COMPLETED".equals(run.status())) return false;
        if (!validPaymentFinalizer(run)) {
            failInvalidFinalizerSnapshot(run);
            return false;
        }
        if (run.steps().stream().allMatch(step -> "COMPLETED".equals(step.status()))) {
            finalizeCompletedRun(run, currentBusinessDate(true), "RECOVERY_COMPLETE");
            return false;
        }
        if (hasActiveLease(run)) return false;

        long requested = actions.countByRunIdAndActionType(run.id(), "RESUME");
        long applied = actions.countByRunIdAndActionType(run.id(), "RESUME_APPLIED");
        if (requested > applied) {
            applyManualContinuation(run, true);
            return true;
        }
        boolean failedFinancialStep = run.steps().stream()
                .filter(step -> !step.definition().finalizer() && step.sequence() >= 6)
                .anyMatch(step -> "FAILED".equals(step.status()));
        if ("FAILED".equals(run.status()) && failedFinancialStep && !paymentFenceProven(run)) {
            markRecoveryFenceRequired(run,
                    "Persisted cutoff/drain output does not prove a common owned payment fence");
            return false;
        }
        if (Set.of("PENDING", "RUNNING").contains(run.status())) return true;
        if (!"FAILED".equals(run.status()) || !hasIncompleteFinalizer(run)) return false;

        boolean heldForMainRecovery = run.steps().stream()
                .anyMatch(step -> "PAYMENTS_REOPEN".equals(step.code())
                        && "FAILED".equals(step.status())
                        && Set.of("HELD_FOR_EOD_RECOVERY", "RECOVERY_FENCE_REQUIRED")
                                .contains(step.errorCode()))
                && run.steps().stream().filter(step -> !step.definition().finalizer())
                        .anyMatch(step -> "FAILED".equals(step.status()));
        if (heldForMainRecovery) return false;

        // A failed business/main control is deliberately not reset here. Recovery is only allowed
        // to reclaim/retry the operational safety finalizer. Business/permanent ownership or
        // configuration failures require an explicit operator continuation.
        EodRunStepEntity failedFinalizer = run.steps().stream()
                .filter(step -> "PAYMENTS_REOPEN".equals(step.code()) && "FAILED".equals(step.status()))
                .findFirst().orElse(null);
        if (failedFinalizer != null) {
            if (!automaticallyRecoverableFinalizerFailure(failedFinalizer)) return false;
            failedFinalizer.resetForResume(true);
            audit(run.id(), "RECOVER_FINALIZER", failedFinalizer.code(), "SYSTEM_RECOVERY",
                    "Retrying the payment-intake safety finalizer with a fresh epoch",
                    Map.of("executionEpoch", failedFinalizer.executionEpoch()));
            runs.saveAndFlush(run);
        }
        return true;
    }

    private boolean automaticallyRecoverableFinalizerFailure(EodRunStepEntity finalizer) {
        return finalizer.failureClass() == null
                || FailureClass.TRANSIENT.name().equals(finalizer.failureClass())
                || "ORCHESTRATION_EXCEPTION".equals(finalizer.errorCode());
    }

    private boolean holdPaymentsForFinancialRecovery(String runId, String workerToken) {
        return inTransaction(() -> {
            EodRunEntity run = requireRunForUpdate(runId);
            boolean persistedMainFailure = run.steps().stream()
                    .filter(step -> !step.definition().finalizer())
                    .anyMatch(step -> "FAILED".equals(step.status()));
            if (!persistedMainFailure) return false;
            boolean financialWorkStarted = run.steps().stream()
                    .filter(step -> !step.definition().finalizer() && step.sequence() >= 6)
                    .anyMatch(step -> !"PENDING".equals(step.status()));
            if (!financialWorkStarted) return false;
            if (!paymentFenceProven(run)) {
                markRecoveryFenceRequired(run,
                        "Current cutoff/drain output does not prove a common owned payment fence");
                return true;
            }

            EodRunStepEntity finalizer = run.steps().stream()
                    .filter(step -> "PAYMENTS_REOPEN".equals(step.code()))
                    .findFirst().orElse(null);
            if (finalizer == null || "COMPLETED".equals(finalizer.status())) return false;
            if ("FAILED".equals(finalizer.status())
                    && "HELD_FOR_EOD_RECOVERY".equals(finalizer.errorCode())) return true;

            finalizer.markRunning(workerToken, OffsetDateTime.now().plusSeconds(leaseSeconds));
            String message = "Payments remain cut off because EOD financial mutations have started; "
                    + "repair and resume the failed control before intake is reopened";
            Map<String, Object> details = Map.of(
                    "failureClass", FailureClass.BUSINESS.name(),
                    "paymentsIntake", "HELD_CLOSED",
                    "recoveryAction", "RESUME_FAILED_EOD_STEP",
                    "workflowVersion", run.workflowVersion());
            run.markFailed(finalizer, "HELD_FOR_EOD_RECOVERY", message, writeJson(details),
                    FailureClass.BUSINESS);
            EodBusinessDateEntity current = currentBusinessDate(true);
            if (current.businessDate().equals(run.businessDate())) current.markFailed();
            audit(run.id(), "PAYMENTS_HELD_FOR_RECOVERY", finalizer.code(), "SYSTEM", message, details);
            runs.saveAndFlush(run);
            businessDates.saveAndFlush(current);
            return true;
        });
    }

    private boolean safelyHasFailedMainStep(String runId) {
        try {
            return inTransaction(() -> requireRun(runId).steps().stream()
                    .filter(step -> !step.definition().finalizer())
                    .anyMatch(step -> "FAILED".equals(step.status())));
        } catch (RuntimeException exception) {
            LOG.error("Unable to inspect failed EOD steps for run {}", runId, exception);
            return true;
        }
    }

    private boolean safelyHasPendingManualContinuation(String runId) {
        try {
            return inTransaction(() -> {
                EodRunEntity run = requireRun(runId);
                if ("COMPLETED".equals(run.status())) return false;
                return actions.countByRunIdAndActionType(runId, "RESUME")
                        > actions.countByRunIdAndActionType(runId, "RESUME_APPLIED");
            });
        } catch (RuntimeException exception) {
            LOG.error("Unable to inspect continuation requests for EOD run {}", runId, exception);
            return false;
        }
    }

    private void bestEffortUnexpectedFailure(String runId, String preferredStepCode, String workerToken,
                                             RuntimeException exception) {
        try {
            inTransaction(() -> {
                EodRunEntity run = requireRunForUpdate(runId);
                if ("COMPLETED".equals(run.status())) return null;
                EodRunStepEntity persistedPreferred = preferredStepCode == null ? null : run.steps().stream()
                        .filter(step -> step.code().equalsIgnoreCase(preferredStepCode))
                        .findFirst().orElse(null);
                if (persistedPreferred != null && ("COMPLETED".equals(persistedPreferred.status())
                        || ("RUNNING".equals(persistedPreferred.status())
                        && !persistedPreferred.leaseOwnedBy(workerToken))
                        || ("PENDING".equals(persistedPreferred.status()) && hasActiveLease(run)))) return null;

                EodRunStepEntity step = persistedPreferred;
                if (step == null) {
                    step = run.steps().stream()
                            .filter(value -> "RUNNING".equals(value.status())
                                    && value.leaseOwnedBy(workerToken))
                            .findFirst().orElse(null);
                }
                if (step == null && !hasActiveLease(run)) {
                    step = run.steps().stream().filter(value -> "PENDING".equals(value.status()))
                            .findFirst().orElse(null);
                }
                if (step == null || "FAILED".equals(step.status())) return null;
                String message = "Unexpected EOD orchestration failure: "
                        + Objects.toString(exception.getMessage(), exception.getClass().getSimpleName());
                Map<String, Object> details = Map.of(
                        "failureClass", FailureClass.PERMANENT.name(),
                        "exceptionType", exception.getClass().getName(),
                        "message", message,
                        "workflowVersion", run.workflowVersion());
                run.markFailed(step, "ORCHESTRATION_EXCEPTION", message, writeJson(details),
                        FailureClass.PERMANENT);
                EodBusinessDateEntity current = currentBusinessDate(true);
                if (current.businessDate().equals(run.businessDate())) current.markFailed();
                audit(run.id(), "ORCHESTRATION_FAILED", step.code(), "SYSTEM", message, details);
                runs.saveAndFlush(run);
                businessDates.saveAndFlush(current);
                return null;
            });
        } catch (RuntimeException persistenceFailure) {
            LOG.error("Unable to persist unexpected EOD failure for run {}", runId, persistenceFailure);
        }
    }

    private boolean dependenciesCompleted(EodRunEntity run, EodRunStepEntity step) {
        return step.dependencies().stream()
                .allMatch(code -> "COMPLETED".equals(run.requireStep(code).status()));
    }

    private boolean fullyCompletedWithFinalizer(EodRunEntity run) {
        return "COMPLETED".equals(run.status())
                && run.steps().stream().allMatch(step -> "COMPLETED".equals(step.status()))
                && run.steps().stream().filter(step -> step.definition().finalizer()).count() == 1
                && run.steps().stream().anyMatch(step -> "PAYMENTS_REOPEN".equals(step.code())
                        && step.definition().finalizer() && "COMPLETED".equals(step.status()));
    }

    private boolean validPaymentFinalizer(EodRunEntity run) {
        long allFinalizers = run.steps().stream().filter(step -> step.definition().finalizer()).count();
        long paymentFinalizers = run.steps().stream()
                .filter(step -> "PAYMENTS_REOPEN".equals(step.code()) && step.definition().finalizer())
                .count();
        return allFinalizers == 1 && paymentFinalizers == 1;
    }

    private void failInvalidFinalizerSnapshot(EodRunEntity run) {
        List<String> finalizerCodes = run.steps().stream().filter(step -> step.definition().finalizer())
                .map(EodRunStepEntity::code).toList();
        String message = "Persisted EOD workflow must contain exactly one PAYMENTS_REOPEN safety finalizer";
        Map<String, Object> details = Map.of(
                "failureClass", FailureClass.PERMANENT.name(),
                "message", message,
                "finalizerCodes", finalizerCodes,
                "workflowVersion", run.workflowVersion());
        boolean newlyRecorded = run.markWorkflowFailed("PAYMENTS_REOPEN", "WORKFLOW_FINALIZER_INVALID",
                writeJson(details));
        EodBusinessDateEntity current = currentBusinessDate(true);
        if (current.businessDate().equals(run.businessDate())) current.markFailed();
        if (newlyRecorded) {
            audit(run.id(), "WORKFLOW_FINALIZER_INVALID", "PAYMENTS_REOPEN", "SYSTEM", message, details);
        }
        runs.saveAndFlush(run);
        businessDates.saveAndFlush(current);
    }

    private boolean hasIncompleteMainStep(EodRunEntity run) {
        return run.steps().stream().filter(step -> !step.definition().finalizer())
                .anyMatch(step -> !"COMPLETED".equals(step.status()));
    }

    private boolean hasIncompleteFinalizer(EodRunEntity run) {
        return run.steps().stream().filter(step -> step.definition().finalizer())
                .anyMatch(step -> !"COMPLETED".equals(step.status()));
    }

    private boolean hasActiveLease(EodRunEntity run) {
        OffsetDateTime now = OffsetDateTime.now();
        return run.steps().stream().anyMatch(step -> step.hasActiveLease(now));
    }

    private Map<String, Map<String, Object>> completedOutputs(EodRunEntity run) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        run.steps().stream().filter(step -> "COMPLETED".equals(step.status()))
                .forEach(step -> result.put(step.code(), readJson(step.outputJson())));
        return result;
    }

    private void requireLease(EodRunStepEntity step, String workerToken) {
        if (!step.leaseOwnedBy(workerToken))
            throw new EodConflictException("EOD step execution lease was lost: " + step.code());
    }

    private void audit(String runId, String actionType, String stepCode, String requestedBy,
                       String reason, Map<String, ?> details) {
        actions.save(new EodRunActionEntity(runId, actionType, stepCode, requestedBy, reason, writeJson(details)));
    }

    private boolean recordActionRequest(String runId, String actionType, String requestKind, String stepCode,
                                        String requestKey, String requestedBy, String reason,
                                        Map<String, Object> requestPayload) {
        String normalizedKey = requestKey == null ? "" : requestKey.trim();
        if (normalizedKey.isEmpty()) {
            audit(runId, actionType, stepCode, requestedBy, reason, requestPayload);
            return true;
        }
        if (normalizedKey.length() > 200) {
            throw new EodConflictException("Idempotency-Key exceeds 200 characters");
        }
        String hash = requestHash(requestPayload);
        var existing = actions.findByRunIdAndRequestKindAndRequestKey(runId, requestKind, normalizedKey);
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().requestHash())) {
                throw new EodConflictException("Idempotency-Key was already used with a different "
                        + requestKind + " request");
            }
            return false;
        }
        Map<String, Object> details = new LinkedHashMap<>(requestPayload);
        details.put("requestKind", requestKind);
        details.put("requestHash", hash);
        actions.saveAndFlush(new EodRunActionEntity(runId, actionType, stepCode, requestedBy, reason,
                writeJson(details), requestKind, normalizedKey, hash));
        return true;
    }

    private String requestHash(Map<String, Object> requestPayload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(writeJson(requestPayload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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

    private EodRunEntity requireRunForUpdate(String runId) {
        return runs.findForUpdate(runId)
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
                        step.providerService(), step.method(), displayPath(step), step.status(), step.commandReference(),
                        step.attemptCount(), instant(step.startedAt()), instant(step.completedAt()),
                        step.errorCode(), step.message(), readJson(step.outputJson()))).toList(),
                run.exceptions().stream().map(value -> new ExceptionResponse(value.id(), value.stepCode(),
                        value.severity(), value.errorCode(), readJson(value.detailsJson()), value.status(),
                        value.resolution(), value.resolvedBy(), instant(value.resolvedAt()))).toList(),
                run.apiVersion());
    }

    /** Internal M2M routing is an execution concern; retain the established operator/UI paths. */
    private String displayPath(EodRunStepEntity step) {
        return switch (step.code()) {
            case "PAYMENTS_CUTOFF" -> "/api/v1/payments/operations/eod/cutoff";
            case "PAYMENTS_DRAIN" -> "/api/v1/payments/operations/eod/drain";
            case "PAYMENTS_REOPEN" -> "/api/v1/payments/operations/eod/reopen";
            case "CREDIT_CARD_READINESS" -> "/api/credit-cards/accounts/eod/readiness";
            case "DEPOSIT_READINESS" -> "/api/deposit-accounts/operations/eod/readiness";
            case "DEPOSIT_ACCRUALS" -> "/api/deposit-accounts/operations/eod/account-accruals";
            case "FIXED_DEPOSIT_ACCRUALS" ->
                    "/api/deposit-accounts/operations/eod/fixed-deposit-accruals";
            case "FIXED_DEPOSIT_MATURITIES" ->
                    "/api/deposit-accounts/operations/eod/fixed-deposit-maturities";
            default -> step.path();
        };
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
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
    private record StepClaim(String runId, String stepCode, StepDefinition definition, String workerToken,
                             EodContext context, Map<String, Map<String, Object>> outputs) {}
    private enum ClaimState { CLAIMED, NONE, BUSY, DEADLOCK, INVALID_FINALIZER }
    private record ClaimDecision(ClaimState state, StepClaim claim, String blockedStepCode) {
        static ClaimDecision claimed(StepClaim claim) { return new ClaimDecision(ClaimState.CLAIMED, claim, null); }
        static ClaimDecision none() { return new ClaimDecision(ClaimState.NONE, null, null); }
        static ClaimDecision busy() { return new ClaimDecision(ClaimState.BUSY, null, null); }
        static ClaimDecision deadlock(String stepCode) { return new ClaimDecision(ClaimState.DEADLOCK, null, stepCode); }
        static ClaimDecision invalidFinalizer() {
            return new ClaimDecision(ClaimState.INVALID_FINALIZER, null, "PAYMENTS_REOPEN");
        }
    }
}
