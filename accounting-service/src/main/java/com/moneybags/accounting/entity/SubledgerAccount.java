package com.moneybags.accounting.entity;

import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.domain.DomainTypes.LifecycleState;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "SUBLEDGER_ACCOUNT", uniqueConstraints = @UniqueConstraint(columnNames = {"ACCOUNT_TYPE", "ACCOUNT_REFERENCE"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubledgerAccount {
    @Id @Column(name = "SUBLEDGER_ACCOUNT_ID", length = 36) private String id;
    @Enumerated(EnumType.STRING) @Column(name = "ACCOUNT_TYPE", length = 30, nullable = false) private AccountType accountType;
    @Column(name = "ACCOUNT_REFERENCE", length = 100, nullable = false) private String accountReference;
    @Column(name = "PRODUCT_CODE", length = 40) private String productCode;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false) private String currencyCode;
    @Enumerated(EnumType.STRING) @Column(name = "LIFECYCLE_STATE", length = 20, nullable = false) private LifecycleState lifecycleState;
    @Column(name = "SOURCE_SERVICE", length = 80, nullable = false) private String sourceService;
    @Column(name = "OPENED_AT", nullable = false) private OffsetDateTime openedAt;
    @Column(name = "CLOSED_AT") private OffsetDateTime closedAt;
    @Version @Column(name = "VERSION_NO", nullable = false) private long version;

    public SubledgerAccount(String id, AccountType accountType, String accountReference, String productCode,
                            String currencyCode, String sourceService, OffsetDateTime openedAt) {
        this.id = id; this.accountType = accountType; this.accountReference = accountReference;
        this.productCode = productCode; this.currencyCode = currencyCode; this.sourceService = sourceService;
        this.lifecycleState = LifecycleState.OPEN; this.openedAt = openedAt;
    }

    public void close(OffsetDateTime closedAt) { this.lifecycleState = LifecycleState.CLOSED; this.closedAt = closedAt; }
}
