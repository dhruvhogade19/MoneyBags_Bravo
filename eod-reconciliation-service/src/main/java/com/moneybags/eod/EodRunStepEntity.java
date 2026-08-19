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

    @Column(name = "ATTEMPT_COUNT", nullable = false, columnDefinition = "NUMBER(10)")
    private int attemptCount;

    @Column(name = "STARTED_AT")
    private OffsetDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Column(name = "ERROR_CODE", length = 80)
    private String errorCode;

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
        this.outputJson = "{}";
    }

    String code() { return code; }
    int sequence() { return sequence; }
    String providerService() { return providerService; }
    String method() { return method; }
    String path() { return path; }
    String status() { return status; }
    String commandReference() { return commandReference; }
    int attemptCount() { return attemptCount; }
    OffsetDateTime startedAt() { return startedAt; }
    OffsetDateTime completedAt() { return completedAt; }
    String errorCode() { return errorCode; }
    String message() { return message; }
    String outputJson() { return outputJson; }

    void markRunning() {
        status = "RUNNING";
        startedAt = OffsetDateTime.now();
        completedAt = null;
        attemptCount++;
    }

    void markCompleted(String outputJson) {
        status = "COMPLETED";
        completedAt = OffsetDateTime.now();
        errorCode = null;
        message = null;
        this.outputJson = outputJson;
    }

    void markFailed(String errorCode, String message) {
        status = "FAILED";
        completedAt = OffsetDateTime.now();
        this.errorCode = errorCode;
        this.message = message;
    }
}
