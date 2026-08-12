package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.AccountStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_STATUS_HISTORY", indexes =
        @Index(name = "IX_STATUS_ACCOUNT_TIME", columnList = "ACCOUNT_ID, CHANGED_AT"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountStatusHistory {
    @Id @Column(name = "HISTORY_ID", length = 36)
    private String id;
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String accountId;
    @Enumerated(EnumType.STRING) @Column(name = "FROM_STATUS", length = 24)
    private AccountStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "TO_STATUS", length = 24, nullable = false)
    private AccountStatus toStatus;
    @Column(name = "REASON_CODE", length = 40, nullable = false)
    private String reasonCode;
    @Column(name = "REASON_TEXT", length = 500)
    private String reasonText;
    @Column(name = "CHANGED_BY", length = 100, nullable = false)
    private String changedBy;
    @Column(name = "ACTOR_TYPE", length = 20, nullable = false)
    private String actorType;
    @Column(name = "CORRELATION_ID", length = 64, nullable = false)
    private String correlationId;
    @Column(name = "CHANGED_AT", nullable = false)
    private OffsetDateTime changedAt;

    public AccountStatusHistory(String id, String accountId, AccountStatus fromStatus, AccountStatus toStatus,
                                String reasonCode, String reasonText, String changedBy, String actorType,
                                String correlationId) {
        this.id = id;
        this.accountId = accountId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reasonCode = reasonCode;
        this.reasonText = reasonText;
        this.changedBy = changedBy;
        this.actorType = actorType;
        this.correlationId = correlationId;
        this.changedAt = OffsetDateTime.now();
    }
}
