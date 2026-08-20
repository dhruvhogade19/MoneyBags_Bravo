package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.PeriodStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCT_ACCOUNTING_PERIOD")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountingPeriod {
    @Id @Column(name = "PERIOD_ID", length = 36) private String id;
    @Column(name = "BUSINESS_DATE", nullable = false, unique = true) private LocalDate businessDate;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private PeriodStatus status;
    @Column(name = "OPENED_AT", nullable = false) private OffsetDateTime openedAt;
    @Column(name = "CLOSED_AT") private OffsetDateTime closedAt;
    @Column(name = "OPENED_BY", length = 100, nullable = false) private String openedBy;
    @Column(name = "CLOSED_BY", length = 100) private String closedBy;
    @Version @Column(name = "VERSION_NO", nullable = false) private long version;

    public AccountingPeriod(String id, LocalDate businessDate, String openedBy) {
        this.id = id; this.businessDate = businessDate; this.openedBy = openedBy;
        this.status = PeriodStatus.OPEN; this.openedAt = OffsetDateTime.now();
    }

    public void close(String actor) {
        this.status = PeriodStatus.CLOSED; this.closedBy = actor; this.closedAt = OffsetDateTime.now();
    }
}
