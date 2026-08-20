package com.moneybags.eod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "EOD_RUN_STEP")
class EodRunStepEntity {
    @Id
    @Column(name = "STEP_ID", length = 100, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RUN_ID", nullable = false)
    private EodRunEntity run;

    @Column(name = "STEP_CODE", length = 60, nullable = false)
    private String code;

    @Column(name = "SEQUENCE_NO", nullable = false, columnDefinition = "NUMBER(3)")
    private int sequence;

    @Column(name = "PROVIDER_SERVICE", length = 80, nullable = false)
    private String providerService;

    @Column(name = "HTTP_METHOD", length = 10, nullable = false)
    private String method;

    @Column(name = "ENDPOINT_PATH", length = 500, nullable = false)
    private String path;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "COMMAND_REFERENCE", length = 100, nullable = false)
    private String commandReference;

    @Column(name = "DEPENDENCY_CODES", length = 1000)
    private String dependencyCodes;

    @Column(name = "EXECUTION_MODE", length = 20, nullable = false)
    private String executionMode;

    @Column(name = "AUTH_MODE", length = 20, nullable = false)
    private String authMode;

    @Column(name = "MAX_ATTEMPTS", nullable = false, columnDefinition = "NUMBER(3)")
    private int maxAttempts;

    @Column(name = "RETRY_BACKOFF_MS", nullable = false, columnDefinition = "NUMBER(10)")
    private long retryBackoffMs;

    @Column(name = "CONTRACT_VERSION", length = 60, nullable = false)
    private String contractVersion;

    @Column(name = "IDEMPOTENCY_SUFFIX", length = 100)
    private String idempotencySuffix;

    @Column(name = "EXECUTION_EPOCH", nullable = false, columnDefinition = "NUMBER(10)")
    private int executionEpoch;

    @Column(name = "EXECUTION_TOKEN", length = 36)
    private String executionToken;

    @Column(name = "LEASE_UNTIL")
    private OffsetDateTime leaseUntil;

    @Column(name = "ATTEMPT_COUNT", nullable = false, columnDefinition = "NUMBER(10)")
    private int attemptCount;

    @Column(name = "STARTED_AT")
    private OffsetDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Column(name = "ERROR_CODE", length = 80)
    private String errorCode;

    @Column(name = "FAILURE_CLASS", length = 20)
    private String failureClass;

    @Lob
    @Column(name = "ERROR_MESSAGE")
    private String message;

    @Lob
    @Column(name = "OUTPUT_JSON", nullable = false)
    private String outputJson;

    protected EodRunStepEntity() {}

    EodRunStepEntity(EodRunEntity run, StepDefinition definition) {
        this.id = run.id() + ":" + definition.code();
        this.run = run;
        this.code = definition.code();
        this.sequence = definition.sequence();
        this.providerService = definition.providerService();
        this.method = definition.method();
        this.path = definition.path();
        this.status = "PENDING";
        this.commandReference = id;
        this.dependencyCodes = String.join(",", definition.dependencies());
        this.executionMode = definition.executionMode().name();
        this.authMode = definition.authMode().name();
        this.maxAttempts = definition.maxAttempts();
        this.retryBackoffMs = definition.retryBackoffMs();
        this.contractVersion = definition.contractVersion();
        this.idempotencySuffix = definition.idempotencySuffix();
        this.executionEpoch = 1;
        this.outputJson = "{}";
    }

    String code() { return code; }
    int sequence() { return sequence; }
    String providerService() { return providerService; }
    String method() { return method; }
    String path() { return path; }
    String status() { return status; }
    String commandReference() { return commandReference; }
    List<String> dependencies() {
        if (dependencyCodes == null || dependencyCodes.isBlank()) return List.of();
        return Arrays.stream(dependencyCodes.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toList();
    }
    StepExecutionMode executionMode() { return StepExecutionMode.valueOf(executionMode); }
    StepAuthMode authMode() { return StepAuthMode.valueOf(authMode); }
    int maxAttempts() { return maxAttempts; }
    long retryBackoffMs() { return retryBackoffMs; }
    String contractVersion() { return contractVersion; }
    String idempotencySuffix() { return idempotencySuffix == null ? "" : idempotencySuffix; }
    int executionEpoch() { return executionEpoch; }
    String executionToken() { return executionToken; }
    OffsetDateTime leaseUntil() { return leaseUntil; }
    int attemptCount() { return attemptCount; }
    OffsetDateTime startedAt() { return startedAt; }
    OffsetDateTime completedAt() { return completedAt; }
    String errorCode() { return errorCode; }
    String failureClass() { return failureClass; }
    String message() { return message; }
    String outputJson() { return outputJson; }

    StepDefinition definition() {
        return new StepDefinition(code, sequence, providerService, method, path, dependencies(),
                executionMode(), authMode(), maxAttempts, retryBackoffMs, contractVersion, idempotencySuffix());
    }

    void markRunning(String token, OffsetDateTime leasedUntil) {
        status = "RUNNING";
        startedAt = OffsetDateTime.now();
        completedAt = null;
        executionToken = token;
        leaseUntil = leasedUntil;
        errorCode = null;
        failureClass = null;
        message = null;
        attemptCount++;
    }

    /** Used by legacy tests and data setup. Runtime execution always uses a lease token. */
    void markRunning() { markRunning("LEGACY-TEST", OffsetDateTime.now().plusMinutes(5)); }

    void markAutomaticRetry(String token, OffsetDateTime leasedUntil, String errorCode,
                            String message, FailureClass classification) {
        requireOwner(token);
        this.errorCode = errorCode;
        this.message = message;
        this.failureClass = classification.name();
        this.leaseUntil = leasedUntil;
        attemptCount++;
    }

    void markCompleted(String outputJson) {
        status = "COMPLETED";
        completedAt = OffsetDateTime.now();
        errorCode = null;
        failureClass = null;
        message = null;
        executionToken = null;
        leaseUntil = null;
        this.outputJson = outputJson;
    }

    void markFailed(String errorCode, String message, FailureClass classification) {
        status = "FAILED";
        completedAt = OffsetDateTime.now();
        this.errorCode = errorCode;
        this.message = message;
        this.failureClass = classification.name();
        executionToken = null;
        leaseUntil = null;
    }

    void markFailed(String errorCode, String message) {
        markFailed(errorCode, message, FailureClass.BUSINESS);
    }

    boolean leaseOwnedBy(String token) { return token != null && token.equals(executionToken); }
    boolean hasActiveLease(OffsetDateTime now) {
        return "RUNNING".equals(status) && executionToken != null && leaseUntil != null && leaseUntil.isAfter(now);
    }

    void resetForResume(boolean advanceEpoch) {
        status = "PENDING";
        startedAt = null;
        completedAt = null;
        errorCode = null;
        failureClass = null;
        message = null;
        executionToken = null;
        leaseUntil = null;
        outputJson = "{}";
        if (advanceEpoch) executionEpoch++;
    }

    void resetForResumeAtEpoch(int targetEpoch) {
        resetForResume(false);
        executionEpoch = Math.max(targetEpoch, 1);
    }

    private void requireOwner(String token) {
        if (!leaseOwnedBy(token)) throw new IllegalStateException("EOD step lease is no longer owned by this execution");
    }
}
