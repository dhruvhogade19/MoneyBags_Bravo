package com.moneybags.deposit.closure.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name="FD_PREMATURE_CLOSURE_CALC")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class FixedDepositPrematureClosureCalculation {
    @Id @Column(name="CALCULATION_ID",length=36) private String id;
    @Column(name="FD_ID",length=36,nullable=false) private String fixedDepositId;
    @Column(name="CLOSURE_REQUEST_ID",length=36,nullable=false,unique=true) private String closureRequestId;
    @Column(name="VALUE_DATE",nullable=false) private LocalDate valueDate;
    @Column(name="REQUESTED_CLOSURE_DATE",nullable=false) private LocalDate requestedClosureDate;
    @Column(name="COMPLETED_HOLDING_DAYS",nullable=false) private long completedHoldingDays;
    @Column(name="ORIGINAL_MATURITY_DATE",nullable=false) private LocalDate originalMaturityDate;
    @Column(name="BOOKED_ANNUAL_RATE",precision=12,scale=8,nullable=false) private BigDecimal bookedAnnualRate;
    @Column(name="APPLICABLE_ANNUAL_RATE",precision=12,scale=8,nullable=false) private BigDecimal applicableAnnualRate;
    @Column(name="PENALTY_RATE",precision=12,scale=8,nullable=false) private BigDecimal penaltyRate;
    @Column(name="FINAL_ANNUAL_RATE",precision=12,scale=8,nullable=false) private BigDecimal finalAnnualRate;
    @Column(name="ACCRUED_INTEREST",precision=19,scale=4,nullable=false) private BigDecimal accruedInterest;
    @Column(name="RECALCULATED_INTEREST",precision=19,scale=4,nullable=false) private BigDecimal recalculatedInterest;
    @Column(name="ALREADY_PAID_INTEREST",precision=19,scale=4,nullable=false) private BigDecimal alreadyPaidInterest;
    @Column(name="INTEREST_RECOVERY_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal interestRecoveryAmount;
    @Column(name="NET_INTEREST_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal netInterestAmount;
    @Column(name="PRINCIPAL_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal principalAmount;
    @Column(name="NET_PAYOUT_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal netPayoutAmount;
    @Lob @Column(name="RULE_SNAPSHOT_JSON",nullable=false) private String ruleSnapshotJson;
    @Column(name="CREATED_AT",nullable=false,updatable=false) private OffsetDateTime createdAt;
    public FixedDepositPrematureClosureCalculation(String id,String fdId,String requestId,LocalDate valueDate,
        LocalDate closureDate,long days,LocalDate maturity,BigDecimal bookedRate,BigDecimal applicableRate,
        BigDecimal penaltyRate,BigDecimal finalRate,BigDecimal accrued,BigDecimal recalculated,BigDecimal paid,
        BigDecimal recovery,BigDecimal netInterest,BigDecimal principal,BigDecimal payout,String snapshot){
        this.id=id;this.fixedDepositId=fdId;this.closureRequestId=requestId;this.valueDate=valueDate;
        this.requestedClosureDate=closureDate;this.completedHoldingDays=days;this.originalMaturityDate=maturity;
        this.bookedAnnualRate=bookedRate;this.applicableAnnualRate=applicableRate;this.penaltyRate=penaltyRate;
        this.finalAnnualRate=finalRate;this.accruedInterest=accrued;this.recalculatedInterest=recalculated;
        this.alreadyPaidInterest=paid;this.interestRecoveryAmount=recovery;this.netInterestAmount=netInterest;
        this.principalAmount=principal;this.netPayoutAmount=payout;this.ruleSnapshotJson=snapshot;this.createdAt=OffsetDateTime.now();}
}
