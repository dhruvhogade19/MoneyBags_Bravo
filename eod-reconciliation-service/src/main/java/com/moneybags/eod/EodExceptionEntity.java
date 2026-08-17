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
import java.util.UUID;

@Entity
@Table(name = "EOD_EXCEPTION")
class EodExceptionEntity {
    @Id
    @Column(name = "EXCEPTION_ID", length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "RUN_ID", nullable = false)
    private EodRunEntity run;

    @Column(name = "STEP_CODE", length = 60, nullable = false)
    private String stepCode;

    @Column(name = "SEVERITY", length = 20, nullable = false)
    private String severity;

    @Column(name = "ERROR_CODE", length = 80, nullable = false)
    private String errorCode;

    @Lob
    @Column(name = "DETAILS_JSON", nullable = false)
    private String detailsJson;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "RESOLUTION", length = 1000)
    private String resolution;

    @Column(name = "RESOLVED_BY", length = 120)
    private String resolvedBy;

    @Column(name = "RESOLVED_AT")
    private OffsetDateTime resolvedAt;

    @Column(name = "CREATED_AT", nullable = false)
    private OffsetDateTime createdAt;

    protected EodExceptionEntity() {}

    EodExceptionEntity(EodRunEntity run, String stepCode, String errorCode, String detailsJson) {
        this.id = UUID.randomUUID().toString();
        this.run = run;
        this.stepCode = stepCode;
        this.severity = "ERROR";
        this.errorCode = errorCode;
        this.detailsJson = detailsJson;
        this.status = "OPEN";
        this.createdAt = OffsetDateTime.now();
    }

    String id() { return id; }
    EodRunEntity run() { return run; }
    String stepCode() { return stepCode; }
    String severity() { return severity; }
    String errorCode() { return errorCode; }
    String detailsJson() { return detailsJson; }
    String status() { return status; }
    String resolution() { return resolution; }
    String resolvedBy() { return resolvedBy; }
    OffsetDateTime resolvedAt() { return resolvedAt; }

    void resolve(String resolution, String resolvedBy, boolean waived) {
        status = waived ? "WAIVED" : "RESOLVED";
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = OffsetDateTime.now();
    }

    void resolveAfterRetry() { resolve("Step retry completed", "SYSTEM", false); }
}
