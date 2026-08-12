package com.moneybags.deposit.entity;

import com.moneybags.deposit.domain.DomainTypes.LimitType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ACCOUNT_LIMIT")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountLimit {
    @Id @Column(name = "LIMIT_ID", length = 36)
    private String id;
    @Column(name = "ACCOUNT_ID", length = 36, nullable = false)
    private String accountId;
    @Enumerated(EnumType.STRING) @Column(name = "LIMIT_TYPE", length = 40, nullable = false)
    private LimitType limitType;
    @Column(name = "AMOUNT", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false)
    private String currencyCode;
    @Column(name = "EFFECTIVE_FROM", nullable = false)
    private OffsetDateTime effectiveFrom;
    @Column(name = "EFFECTIVE_TO")
    private OffsetDateTime effectiveTo;
    @Version @Column(name = "VERSION_NO", nullable = false)
    private long version;

    public AccountLimit(String id, String accountId, LimitType limitType, BigDecimal amount,
                        String currencyCode, OffsetDateTime effectiveFrom, OffsetDateTime effectiveTo) {
        this.id = id;
        this.accountId = accountId;
        this.limitType = limitType;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
