package com.moneybags.eod;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "EOD_RUN")
class EodRunEntity {
    @Id
    @Column(name = "RUN_ID", length = 36, nullable = false)
    private String id;

    @Column(name = "IDEMPOTENCY_KEY", length = 200, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "BUSINESS_DATE", nullable = false)
    private LocalDate businessDate;

    @Column(name = "STATUS", length = 20, nullable = false)
    private String status;

    @Column(name = "STARTED_BY", length = 120, nullable = false)
    private String startedBy;

    @Column(name = "STARTED_AT", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false, columnDefinition = "NUMBER(19)")
    private long version;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    private List<EodRunStepEntity> steps = new ArrayList<>();

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<EodExceptionEntity> exceptions = new ArrayList<>();

    protected EodRunEntity() {}

    EodRunEntity(String id, String idempotencyKey, LocalDate businessDate, String startedBy,
                 List<StepDefinition> definitions) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.businessDate = businessDate;
        this.status = "PENDING";
        this.startedBy = startedBy;
        this.startedAt = OffsetDateTime.now();
        definitions.forEach(definition -> steps.add(new EodRunStepEntity(this, definition)));
    }

    String id() { return id; }
    LocalDate businessDate() { return businessDate; }
    String status() { return status; }
    String startedBy() { return startedBy; }
    OffsetDateTime startedAt() { return startedAt; }
    OffsetDateTime completedAt() { return completedAt; }
    long apiVersion() { return version + 1; }
    List<EodRunStepEntity> steps() { return steps; }
    List<EodExceptionEntity> exceptions() { return exceptions; }

    EodRunStepEntity requireStep(String stepCode) {
        return steps.stream().filter(step -> step.code().equalsIgnoreCase(stepCode)).findFirst()
                .orElseThrow(() -> new EodNotFoundException("EOD step not found: " + stepCode));
    }

    void markRunning() {
        status = "RUNNING";
        completedAt = null;
    }

    void markFailed(EodRunStepEntity step, String errorCode, String message, String detailsJson) {
        status = "FAILED";
        step.markFailed(errorCode, message);
        exceptions.add(new EodExceptionEntity(this, step.code(), errorCode, detailsJson));
    }

    void markCompleted() {
        status = "COMPLETED";
        completedAt = OffsetDateTime.now();
    }
}
