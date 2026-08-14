package com.moneybags.deposit.fixeddeposit.entity;

import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.entity.DepositAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "FIXED_DEPOSIT")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FixedDeposit {
    @Id @Column(name = "FD_ID", length = 36) private String id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ACCOUNT_ID", unique = true)
    private DepositAccount account;
    @Column(name = "PRINCIPAL_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal principal;
    @Column(name = "CURRENCY_CODE", length = 3, columnDefinition = "CHAR(3)", nullable = false)
    private String currencyCode;
    @Column(name = "BOOKING_DATE", nullable = false) private LocalDate bookingDate;
    @Column(name = "VALUE_DATE", nullable = false) private LocalDate valueDate;
    @Column(name = "MATURITY_DATE", nullable = false) private LocalDate maturityDate;
    @Column(name = "TENURE_VALUE", nullable = false) private int tenureValue;
    @Enumerated(EnumType.STRING) @Column(name = "TENURE_UNIT", length = 10, nullable = false) private TenureUnit tenureUnit;
    @Column(name = "BOOKED_ANNUAL_RATE", precision = 12, scale = 8, nullable = false) private BigDecimal bookedAnnualRate;
    @Column(name = "CALCULATION_METHOD", length = 30, nullable = false) private String calculationMethod;
    @Enumerated(EnumType.STRING) @Column(name = "COMPOUNDING_FREQUENCY", length = 20, nullable = false)
    private CompoundingFrequency compoundingFrequency;
    @Enumerated(EnumType.STRING) @Column(name = "PAYOUT_FREQUENCY", length = 20, nullable = false)
    private InterestPayoutFrequency payoutFrequency;
    @Enumerated(EnumType.STRING) @Column(name = "DAY_COUNT_CONVENTION", length = 20, nullable = false)
    private DayCountConvention dayCountConvention;
    @Column(name = "EXPECTED_INTEREST", precision = 19, scale = 4, nullable = false) private BigDecimal expectedInterest;
    @Column(name = "EXPECTED_MATURITY_AMOUNT", precision = 19, scale = 4, nullable = false) private BigDecimal expectedMaturityAmount;
    @Column(name = "ACCRUED_INTEREST", precision = 19, scale = 4, nullable = false) private BigDecimal accruedInterest;
    @Column(name = "PAID_INTEREST", precision = 19, scale = 4, nullable = false) private BigDecimal paidInterest;
    @Column(name = "FUNDING_ACCOUNT_ID", length = 36, nullable = false) private String fundingAccountId;
    @Column(name = "PAYOUT_ACCOUNT_ID", length = 36, nullable = false) private String payoutAccountId;
    @Column(name = "LAST_ACCRUAL_DATE") private LocalDate lastAccrualDate;
    @Enumerated(EnumType.STRING) @Column(name = "STATUS", length = 30, nullable = false) private FixedDepositStatus status;
    @Version @Column(name = "VERSION_NO", nullable = false) private long version;
    @Column(name = "CREATED_AT", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "UPDATED_AT", nullable = false) private OffsetDateTime updatedAt;

    public FixedDeposit(String id, DepositAccount account, BigDecimal principal, String currencyCode,
                        LocalDate valueDate, LocalDate maturityDate, int tenureValue, TenureUnit tenureUnit,
                        BigDecimal rate, String calculationMethod, CompoundingFrequency compoundingFrequency,
                        InterestPayoutFrequency payoutFrequency, DayCountConvention dayCountConvention,
                        BigDecimal expectedInterest, BigDecimal expectedMaturityAmount,
                        String fundingAccountId, String payoutAccountId) {
        this.id = id; this.account = account; this.principal = principal; this.currencyCode = currencyCode;
        this.bookingDate = LocalDate.now(); this.valueDate = valueDate; this.maturityDate = maturityDate;
        this.tenureValue = tenureValue; this.tenureUnit = tenureUnit; this.bookedAnnualRate = rate;
        this.calculationMethod = calculationMethod; this.compoundingFrequency = compoundingFrequency;
        this.payoutFrequency = payoutFrequency; this.dayCountConvention = dayCountConvention;
        this.expectedInterest = expectedInterest; this.expectedMaturityAmount = expectedMaturityAmount;
        this.accruedInterest = BigDecimal.ZERO.setScale(4); this.paidInterest = BigDecimal.ZERO.setScale(4);
        this.fundingAccountId = fundingAccountId; this.payoutAccountId = payoutAccountId;
        this.status = FixedDepositStatus.PENDING_FUNDING; this.createdAt = OffsetDateTime.now(); this.updatedAt = createdAt;
    }
}
