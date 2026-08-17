package com.moneybags.eod.service;

import com.moneybags.eod.api.EodRequests.EodExceptionResolutionRequest;
import com.moneybags.eod.api.EodRequests.EodResumeRequest;
import com.moneybags.eod.api.EodRequests.EodStepRetryRequest;
import com.moneybags.eod.api.EodRequests.OpenBusinessDateRequest;
import com.moneybags.eod.api.EodRequests.StartEodRunRequest;
import com.moneybags.eod.api.EodResponses.BusinessDateResponse;
import com.moneybags.eod.api.EodResponses.EodRunResponse;
import com.moneybags.eod.domain.EodDomain.BusinessDateState;
import com.moneybags.eod.domain.EodDomain.BusinessDateStatus;
import com.moneybags.eod.domain.EodDomain.EodExceptionRecord;
import com.moneybags.eod.domain.EodDomain.EodRun;
import com.moneybags.eod.domain.EodDomain.EodStep;
import com.moneybags.eod.domain.EodDomain.ExceptionStatus;
import com.moneybags.eod.domain.EodDomain.RunStatus;
import com.moneybags.eod.domain.EodDomain.StepDefinition;
import com.moneybags.eod.domain.EodDomain.StepStatus;
import com.moneybags.eod.exception.EodApiException;
import com.moneybags.eod.port.BusinessDateRepository;
import com.moneybags.eod.port.EodRunRepository;
import com.moneybags.eod.port.IdempotencyStore;
import com.moneybags.eod.port.PeerOperations;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EodOrchestrationService {
    private static final String START_SCOPE = "START_EOD_RUN";
    private final EodRunRepository runs;
    private final BusinessDateRepository dates;
    private final IdempotencyStore idempotency;
    private final PeerOperations peers;
    private final Clock clock;

    public EodOrchestrationService(EodRunRepository runs, BusinessDateRepository dates, IdempotencyStore idempotency,
                                   PeerOperations peers, Clock clock) {
        this.runs = runs; this.dates = dates; this.idempotency = idempotency; this.peers = peers; this.clock = clock;
    }

    public synchronized BusinessDateResponse currentBusinessDate() { return BusinessDateResponse.from(currentDate()); }

    public synchronized EodRunResponse start(String key, StartEodRunRequest request) {
        String fingerprint = hash(request.businessDate() + "|" + request.startedBy());
        var replay = idempotency.find(START_SCOPE, key);
        if (replay.isPresent()) {
            if (!replay.get().requestFingerprint().equals(fingerprint))
                throw conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key was already used with a different request");
            return EodRunResponse.from(requireRun(replay.get().resourceId()));
        }
        BusinessDateState date = currentDate();
        if (!date.businessDate().equals(request.businessDate()))
            throw conflict("BUSINESS_DATE_MISMATCH", "Requested date is not the current business date");
        if (date.status() != BusinessDateStatus.OPEN)
            throw conflict("BUSINESS_DATE_NOT_OPEN", "The current business date is not open");
        runs.findByBusinessDate(request.businessDate()).ifPresent(existing -> {
            throw conflict("EOD_RUN_ALREADY_EXISTS", "An EOD run already exists for this business date: " + existing.runId());
        });

        Instant now = Instant.now(clock);
        String runId = "EOD-" + request.businessDate().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 8);
        EodRun run = new EodRun(runId, request.businessDate(), request.startedBy(), now);
        runs.save(run);
        idempotency.save(new IdempotencyStore.Entry(START_SCOPE, key, fingerprint, runId));
        date.cutoff(now);
        dates.save(date);
        executePending(run);
        return EodRunResponse.from(run);
    }

    public synchronized EodRunResponse get(String runId) { return EodRunResponse.from(requireRun(runId)); }

    public synchronized EodRunResponse resume(String runId, EodResumeRequest request) {
        EodRun run = requireRun(runId);
        if (run.status() == RunStatus.COMPLETED) return EodRunResponse.from(run);
        boolean unresolved = run.exceptions().stream().anyMatch(e -> e.status() == ExceptionStatus.OPEN);
        if (unresolved) throw conflict("UNRESOLVED_EOD_EXCEPTION", "Resolve or waive open exceptions before resuming");
        run.resume();
        executePending(run);
        return EodRunResponse.from(run);
    }

    public synchronized EodRunResponse retry(String runId, String stepCode, EodStepRetryRequest request) {
        EodRun run = requireRun(runId);
        EodStep step = findStep(run, stepCode);
        if (step.status() != StepStatus.FAILED)
            throw conflict("STEP_NOT_FAILED", "Only a failed step can be retried");
        run.resume();
        executeOne(run, step);
        if (step.status() == StepStatus.COMPLETED) {
            run.exceptions().stream()
                    .filter(e -> e.stepCode().equals(step.definition().name()) && e.status() == ExceptionStatus.OPEN)
                    .forEach(e -> e.resolve("Step retry succeeded: " + request.reason(), request.requestedBy(), false, Instant.now(clock)));
            finishIfComplete(run);
        }
        runs.save(run);
        return EodRunResponse.from(run);
    }

    public synchronized EodRunResponse resolve(String exceptionId, EodExceptionResolutionRequest request) {
        EodRun run = runs.findByExceptionId(exceptionId)
                .orElseThrow(() -> notFound("EOD_EXCEPTION_NOT_FOUND", "EOD exception not found: " + exceptionId));
        EodExceptionRecord exception = run.exceptions().stream().filter(e -> e.exceptionId().equals(exceptionId)).findFirst().orElseThrow();
        if (exception.status() != ExceptionStatus.OPEN)
            throw conflict("EXCEPTION_ALREADY_RESOLVED", "The EOD exception is already resolved or waived");
        exception.resolve(request.resolution(), request.resolvedBy(), request.waived(), Instant.now(clock));
        run.touch();
        runs.save(run);
        return EodRunResponse.from(run);
    }

    public synchronized BusinessDateResponse openNext(OpenBusinessDateRequest request) {
        BusinessDateState current = currentDate();
        if (current.status() != BusinessDateStatus.CLOSED)
            throw conflict("CURRENT_DATE_NOT_CLOSED", "The current business date must be closed before opening the next date");
        if (!request.businessDate().equals(current.businessDate().plusDays(1)))
            throw conflict("INVALID_NEXT_BUSINESS_DATE", "The next business date must be exactly one day after the closed date");
        String reference = "OPEN-DATE:" + request.businessDate();
        PeerOperations.Result result = peers.openAccountingPeriod(request.businessDate(), request.openedBy(), reference);
        if (!result.successful()) throw new EodApiException(HttpStatus.SERVICE_UNAVAILABLE, result.code(), result.message());
        BusinessDateState next = new BusinessDateState(request.businessDate(), Instant.now(clock));
        dates.save(next);
        return BusinessDateResponse.from(next);
    }

    private void executePending(EodRun run) {
        for (EodStep step : run.steps()) {
            if (step.status() == StepStatus.PENDING || step.status() == StepStatus.FAILED) {
                executeOne(run, step);
                if (step.status() == StepStatus.FAILED) break;
            }
        }
        finishIfComplete(run);
        runs.save(run);
    }

    private void executeOne(EodRun run, EodStep step) {
        step.start(Instant.now(clock));
        PeerOperations.Result result = peers.execute(step.definition(), peerRequest(run, step));
        if (result.successful()) {
            step.complete(Instant.now(clock), result.message(), result.payload());
            run.touch();
        } else {
            step.fail(Instant.now(clock), result.code(), result.message(), result.payload());
            run.exceptions().add(new EodExceptionRecord(UUID.randomUUID().toString(), step.definition().name(), result.code(), result.message()));
            run.block();
        }
    }

    private PeerOperations.Request peerRequest(EodRun run, EodStep step) {
        Map<String, Object> body = Map.of();
        if (step.definition().requestBodyRequired()) {
            Map<String, Object> mutableBody = new LinkedHashMap<>();
            mutableBody.put("eodRunId", run.runId());
            mutableBody.put("commandReference", step.commandReference());
            mutableBody.put("businessDate", run.businessDate());
            if (step.definition() == StepDefinition.DEPOSIT_ACCRUALS) mutableBody.put("currency", "INR");
            body = Map.copyOf(mutableBody);
        }
        Map<String, String> headers = step.definition().idempotencyKeyRequired()
                ? Map.of("Idempotency-Key", step.commandReference()) : Map.of();
        return new PeerOperations.Request(run.runId(), run.businessDate(), step.commandReference(), body, headers);
    }

    private void finishIfComplete(EodRun run) {
        if (run.steps().stream().allMatch(step -> step.status() == StepStatus.COMPLETED)) {
            Instant now = Instant.now(clock);
            run.complete(now);
            BusinessDateState date = currentDate();
            if (date.businessDate().equals(run.businessDate())) { date.close(now); dates.save(date); }
        }
    }

    private EodStep findStep(EodRun run, String stepCode) {
        StepDefinition definition;
        try { definition = StepDefinition.valueOf(stepCode.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw notFound("EOD_STEP_NOT_FOUND", "Unknown EOD step: " + stepCode); }
        return run.steps().stream().filter(step -> step.definition() == definition).findFirst()
                .orElseThrow(() -> notFound("EOD_STEP_NOT_FOUND", "Step does not exist in this run"));
    }

    private EodRun requireRun(String id) { return runs.findById(id).orElseThrow(() -> notFound("EOD_RUN_NOT_FOUND", "EOD run not found: " + id)); }
    private BusinessDateState currentDate() { return dates.current().orElseThrow(() -> new EodApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_DATE_MISSING", "No current business date is configured")); }
    private EodApiException conflict(String code, String message) { return new EodApiException(HttpStatus.CONFLICT, code, message); }
    private EodApiException notFound(String code, String message) { return new EodApiException(HttpStatus.NOT_FOUND, code, message); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
