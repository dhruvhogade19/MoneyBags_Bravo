package com.moneybags.eod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "EOD_BUSINESS_DATE")
class EodBusinessDateEntity {
    static final long CURRENT_RECORD_ID = 1L;

    @Id
    @Column(name = "RECORD_ID", nullable = false, columnDefinition = "NUMBER(1)")
    private Long id;

    @Column(name = "BUSINESS_DATE", nullable = false)
    private LocalDate businessDate;

    @Column(name = "STATUS", length = 24, nullable = false)
    private String status;

    @Column(name = "CUTOFF_AT")
    private OffsetDateTime cutoffAt;

    @Column(name = "OPENED_AT", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "CLOSED_AT")
    private OffsetDateTime closedAt;

    @Version
    @Column(name = "VERSION_NO", nullable = false, columnDefinition = "NUMBER(19)")
    private long version;

    protected EodBusinessDateEntity() {}

    EodBusinessDateEntity(LocalDate businessDate) {
        this.id = CURRENT_RECORD_ID;
        this.businessDate = businessDate;
        this.status = "OPEN";
        this.openedAt = OffsetDateTime.now();
        this.version = 1;
    }

    LocalDate businessDate() { return businessDate; }
    String status() { return status; }
    OffsetDateTime cutoffAt() { return cutoffAt; }
    OffsetDateTime openedAt() { return openedAt; }
    OffsetDateTime closedAt() { return closedAt; }
    long version() { return version; }

    void startEod() {
        status = "EOD_IN_PROGRESS";
        if (cutoffAt == null) cutoffAt = OffsetDateTime.now();
    }

    void markFailed() { status = "EOD_FAILED"; }

    /**
     * Move the local date before releasing the remote payment fence. The date remains non-open
     * until the finalizer response is durably checkpointed.
     */
    void prepareNextDate(LocalDate nextDate) {
        businessDate = nextDate;
        status = "EOD_IN_PROGRESS";
        openedAt = OffsetDateTime.now();
        cutoffAt = null;
        closedAt = null;
    }

    void openPreparedDate() {
        status = "OPEN";
        if (openedAt == null) openedAt = OffsetDateTime.now();
        cutoffAt = null;
        closedAt = null;
    }

    void advanceTo(LocalDate nextDate) {
        prepareNextDate(nextDate);
        openPreparedDate();
    }
}
