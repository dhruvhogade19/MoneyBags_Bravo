package com.moneybags.deposit.fixeddeposit.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "FD_RATE_SNAPSHOT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedDepositRateSnapshot {
    @Id @Column(name = "SNAPSHOT_ID", length = 36) private String id;
    @Column(name = "FD_ID", length = 36, nullable = false, unique = true) private String fixedDepositId;
    @Column(name = "PRODUCT_CODE", length = 40, nullable = false) private String productCode;
    @Column(name = "PRODUCT_VERSION", nullable = false) private Long productVersion;
    @Column(name = "RATE_SLAB_CODE", length = 80) private String rateSlabCode;
    @Column(name = "INTEREST_POLICY_VERSION", length = 30) private String interestPolicyVersion;
    @Column(name = "ANNUAL_RATE", precision = 12, scale = 8, nullable = false) private BigDecimal annualRate;
    @Column(name = "CALCULATION_METHOD", length = 30, nullable = false) private String calculationMethod;
    @Column(name = "COMPOUNDING_FREQUENCY", length = 20, nullable = false) private String compoundingFrequency;
    @Column(name = "PAYOUT_FREQUENCY", length = 20, nullable = false) private String payoutFrequency;
    @Column(name = "DAY_COUNT_CONVENTION", length = 20, nullable = false) private String dayCountConvention;
    @Lob @Column(name = "RULE_SNAPSHOT_JSON", nullable = false) private String ruleSnapshotJson;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;

    public FixedDepositRateSnapshot(String id, String fdId, String productCode, Long productVersion,
                                    String slabCode, String policyVersion, BigDecimal rate, String method,
                                    String compounding, String payout, String dayCount, String json) {
        this.id=id; this.fixedDepositId=fdId; this.productCode=productCode; this.productVersion=productVersion;
        this.rateSlabCode=slabCode; this.interestPolicyVersion=policyVersion; this.annualRate=rate;
        this.calculationMethod=method; this.compoundingFrequency=compounding; this.payoutFrequency=payout;
        this.dayCountConvention=dayCount; this.ruleSnapshotJson=json; this.createdAt=OffsetDateTime.now();
    }
}
