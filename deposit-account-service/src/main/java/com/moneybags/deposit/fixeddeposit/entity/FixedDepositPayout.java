package com.moneybags.deposit.fixeddeposit.entity;

import com.moneybags.deposit.domain.DomainTypes.FixedDepositPayoutStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "FD_PAYOUT")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedDepositPayout {
    @Id @Column(name = "PAYOUT_ID", length = 36) private String id;
    @Column(name = "FD_ID", length = 36, nullable = false) private String fixedDepositId;
    @Column(name = "PAYOUT_TYPE", length = 20, nullable = false) private String payoutType;
    @Column(name = "PRINCIPAL_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal principal;
    @Column(name = "INTEREST_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal interest;
    @Column(name = "NET_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal netAmount;
    @Column(name = "DESTINATION_ACCOUNT_ID", length = 36, nullable = false) private String destinationAccountId;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 20, nullable = false) private FixedDepositPayoutStatus status;
    @Column(name = "SOURCE_REFERENCE", length = 100, nullable = false, unique = true) private String sourceReference;
    @Column(name = "FAILURE_CODE", length = 80) private String failureCode;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "COMPLETED_AT") private OffsetDateTime completedAt;

    public FixedDepositPayout(String id, String fdId, BigDecimal principal, BigDecimal interest,
                              String destinationAccountId, String sourceReference) {
        this(id,fdId,"MATURITY",principal,interest,destinationAccountId,sourceReference);
    }

    public FixedDepositPayout(String id, String fdId, String payoutType, BigDecimal principal, BigDecimal interest,
                              String destinationAccountId, String sourceReference) {
        this.id=id; this.fixedDepositId=fdId; this.payoutType=payoutType; this.principal=principal;
        this.interest=interest; this.netAmount=principal.add(interest); this.destinationAccountId=destinationAccountId;
        this.status=FixedDepositPayoutStatus.PENDING; this.sourceReference=sourceReference; this.createdAt=OffsetDateTime.now();
    }
}
