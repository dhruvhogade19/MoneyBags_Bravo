package com.moneybags.deposit.closure.entity;

import com.moneybags.deposit.domain.DomainTypes.ClosureSettlementStatus;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity @Table(name="ACCOUNT_CLOSURE_SETTLEMENT")
@Getter @Setter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AccountClosureSettlement {
    @Id @Column(name="SETTLEMENT_ID",length=36) private String id;
    @Column(name="CLOSURE_REQUEST_ID",length=36,nullable=false,unique=true) private String closureRequestId;
    @Column(name="PRINCIPAL_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal principalAmount;
    @Column(name="ORIGINAL_INTEREST_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal originalInterestAmount;
    @Column(name="RECALCULATED_INTEREST_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal recalculatedInterestAmount;
    @Column(name="INTEREST_PENALTY_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal interestPenaltyAmount;
    @Column(name="CLOSURE_FEE_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal closureFeeAmount;
    @Column(name="TAX_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal taxAmount;
    @Column(name="NET_PAYOUT_AMOUNT",precision=19,scale=4,nullable=false) private BigDecimal netPayoutAmount;
    @Column(name="CURRENCY_CODE",length=3,columnDefinition="CHAR(3)",nullable=false) private String currencyCode;
    @Column(name="DESTINATION_ACCOUNT_ID",length=36) private String destinationAccountId;
    @Column(name="TRANSACTION_REFERENCE",length=100,nullable=false,unique=true) private String transactionReference;
    @Enumerated(EnumType.STRING) @Column(name="STATUS",length=20,nullable=false) private ClosureSettlementStatus status;
    @Column(name="FAILURE_CODE",length=80) private String failureCode;
    @Column(name="CREATED_AT",nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="COMPLETED_AT") private OffsetDateTime completedAt;
    public AccountClosureSettlement(String id,String requestId,BigDecimal principal,BigDecimal originalInterest,
        BigDecimal recalculatedInterest,BigDecimal penalty,BigDecimal fee,BigDecimal tax,BigDecimal net,
        String currency,String destination,String reference){this.id=id;this.closureRequestId=requestId;
        this.principalAmount=principal;this.originalInterestAmount=originalInterest;this.recalculatedInterestAmount=recalculatedInterest;
        this.interestPenaltyAmount=penalty;this.closureFeeAmount=fee;this.taxAmount=tax;this.netPayoutAmount=net;
        this.currencyCode=currency;this.destinationAccountId=destination;this.transactionReference=reference;
        this.status=ClosureSettlementStatus.PENDING;this.createdAt=OffsetDateTime.now();}
    public void complete(){this.status=ClosureSettlementStatus.COMPLETED;this.completedAt=OffsetDateTime.now();}
}
