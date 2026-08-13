package com.moneybags.productmaster.entity;

import com.moneybags.productmaster.domain.Enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.type.NumericBooleanConverter;

/** Value objects persisted in the product-specific tables. They deliberately contain no generic
 * PRODUCT foreign key. */
public final class CatalogRuleValues {
    private CatalogRuleValues() {}

    @Embeddable
    public static class Fee {
        @Enumerated(EnumType.STRING) @Column(name = "FEE_TYPE", nullable = false, length = 30)
        private FeeType feeType;
        @Column(name = "FEE_AMOUNT", precision = 19, scale = 4) private BigDecimal feeAmount;
        @Column(name = "FEE_PERCENTAGE", precision = 9, scale = 4) private BigDecimal feePercentage;
        @Enumerated(EnumType.STRING) @Column(name = "FEE_FREQUENCY", nullable = false, length = 20)
        private FeeFrequency frequency;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE", nullable = false, columnDefinition = "NUMBER(1)") private boolean active;
        public FeeType getFeeType() { return feeType; } public void setFeeType(FeeType value) { feeType = value; }
        public BigDecimal getFeeAmount() { return feeAmount; } public void setFeeAmount(BigDecimal value) { feeAmount = value; }
        public BigDecimal getFeePercentage() { return feePercentage; } public void setFeePercentage(BigDecimal value) { feePercentage = value; }
        public FeeFrequency getFrequency() { return frequency; } public void setFrequency(FeeFrequency value) { frequency = value; }
        public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    }

    @Embeddable
    public static class Eligibility {
        @Column(name = "MINIMUM_AGE") private Integer minimumAge;
        @Column(name = "MAXIMUM_AGE") private Integer maximumAge;
        @Column(name = "MINIMUM_MONTHLY_INCOME", precision = 19, scale = 4) private BigDecimal minimumMonthlyIncome;
        @Enumerated(EnumType.STRING) @Column(name = "CUSTOMER_TYPE", nullable = false, length = 20)
        private CustomerType customerType;
        @Enumerated(EnumType.STRING) @Column(name = "CUSTOMER_CATEGORY", length = 30)
        private CustomerCategory customerCategory;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "KYC_REQUIRED", nullable = false, columnDefinition = "NUMBER(1)") private boolean kycRequired;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "COLLATERAL_REQUIRED", nullable = false, columnDefinition = "NUMBER(1)") private boolean collateralRequired;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE", nullable = false, columnDefinition = "NUMBER(1)") private boolean active;
        public Integer getMinimumAge() { return minimumAge; } public void setMinimumAge(Integer value) { minimumAge = value; }
        public Integer getMaximumAge() { return maximumAge; } public void setMaximumAge(Integer value) { maximumAge = value; }
        public BigDecimal getMinimumMonthlyIncome() { return minimumMonthlyIncome; } public void setMinimumMonthlyIncome(BigDecimal value) { minimumMonthlyIncome = value; }
        public CustomerType getCustomerType() { return customerType; } public void setCustomerType(CustomerType value) { customerType = value; }
        public CustomerCategory getCustomerCategory() { return customerCategory; } public void setCustomerCategory(CustomerCategory value) { customerCategory = value; }
        public boolean isKycRequired() { return kycRequired; } public void setKycRequired(boolean value) { kycRequired = value; }
        public boolean isCollateralRequired() { return collateralRequired; } public void setCollateralRequired(boolean value) { collateralRequired = value; }
        public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    }

    @Embeddable
    public static class Feature {
        @Column(name = "FEATURE_NAME", nullable = false, length = 100) private String featureName;
        @Column(name = "FEATURE_VALUE", length = 500) private String featureValue;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE", nullable = false, columnDefinition = "NUMBER(1)") private boolean active;
        public String getFeatureName() { return featureName; } public void setFeatureName(String value) { featureName = value; }
        public String getFeatureValue() { return featureValue; } public void setFeatureValue(String value) { featureValue = value; }
        public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    }

    @Embeddable
    public static class DepositAmountRule {
        @Column(name = "MIN_OPEN_BAL", precision = 19, scale = 4) private BigDecimal minimumOpeningBalance;
        @Column(name = "MIN_BALANCE", precision = 19, scale = 4) private BigDecimal minimumBalance;
        @Column(name = "MAX_BALANCE", precision = 19, scale = 4) private BigDecimal maximumBalance;
        @Column(name = "MIN_AMOUNT", precision = 19, scale = 4) private BigDecimal minimumAmount;
        @Column(name = "MAX_AMOUNT", precision = 19, scale = 4) private BigDecimal maximumAmount;
        @Column(name = "MIN_TENURE_MONTHS") private Integer minimumTenureMonths;
        @Column(name = "MAX_TENURE_MONTHS") private Integer maximumTenureMonths;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "OVERDRAFT_ALLOWED", nullable = false, columnDefinition = "NUMBER(1)") private boolean overdraftAllowed;
        @Column(name = "OVERDRAFT_LIMIT", precision = 19, scale = 4) private BigDecimal overdraftLimit;
        public BigDecimal getMinimumOpeningBalance() { return minimumOpeningBalance; } public void setMinimumOpeningBalance(BigDecimal value) { minimumOpeningBalance = value; }
        public BigDecimal getMinimumBalance() { return minimumBalance; } public void setMinimumBalance(BigDecimal value) { minimumBalance = value; }
        public BigDecimal getMaximumBalance() { return maximumBalance; } public void setMaximumBalance(BigDecimal value) { maximumBalance = value; }
        public BigDecimal getMinimumAmount() { return minimumAmount; } public void setMinimumAmount(BigDecimal value) { minimumAmount = value; }
        public BigDecimal getMaximumAmount() { return maximumAmount; } public void setMaximumAmount(BigDecimal value) { maximumAmount = value; }
        public Integer getMinimumTenureMonths() { return minimumTenureMonths; } public void setMinimumTenureMonths(Integer value) { minimumTenureMonths = value; }
        public Integer getMaximumTenureMonths() { return maximumTenureMonths; } public void setMaximumTenureMonths(Integer value) { maximumTenureMonths = value; }
        public boolean isOverdraftAllowed() { return overdraftAllowed; } public void setOverdraftAllowed(boolean value) { overdraftAllowed = value; }
        public BigDecimal getOverdraftLimit() { return overdraftLimit; } public void setOverdraftLimit(BigDecimal value) { overdraftLimit = value; }
    }

    @Embeddable
    public static class FixedDepositRule {
        @Column(name = "FD_ALLOWED_TENURE_UNITS", length = 200) private String allowedTenureUnits;
        @Column(name = "FD_ALLOWED_PAYOUTS", length = 200) private String allowedInterestPayoutFrequencies;
        @Enumerated(EnumType.STRING) @Column(name = "FD_DEFAULT_PAYOUT", length = 20)
        private InterestPostingFrequency defaultInterestPayoutFrequency;
        @Enumerated(EnumType.STRING) @Column(name = "FD_COMPOUND_FREQ", length = 20)
        private InterestFrequency compoundingFrequency;
        @Enumerated(EnumType.STRING) @Column(name = "FD_DAY_COUNT", length = 20)
        private DayCountConvention dayCountConvention;
        @Column(name = "FD_MIN_HOLD_DAYS") private Integer minimumHoldingDays;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "FD_PARTIAL_WITHDRAWAL", nullable = false, columnDefinition = "NUMBER(1)") private boolean partialWithdrawalAllowed;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "FD_AUTO_RENEWAL", nullable = false, columnDefinition = "NUMBER(1)") private boolean autoRenewalAllowed;
        @Column(name = "FD_MATURITY_INSTRUCTION", length = 60) private String defaultMaturityInstruction;
        @Column(name = "FD_MAX_RENEWAL_COUNT") private Integer maximumRenewalCount;
        @Column(name = "FD_GRACE_PERIOD_DAYS") private Integer gracePeriodDays;
        public String getAllowedTenureUnits() { return allowedTenureUnits; } public void setAllowedTenureUnits(String value) { allowedTenureUnits = value; }
        public String getAllowedInterestPayoutFrequencies() { return allowedInterestPayoutFrequencies; } public void setAllowedInterestPayoutFrequencies(String value) { allowedInterestPayoutFrequencies = value; }
        public InterestPostingFrequency getDefaultInterestPayoutFrequency() { return defaultInterestPayoutFrequency; } public void setDefaultInterestPayoutFrequency(InterestPostingFrequency value) { defaultInterestPayoutFrequency = value; }
        public InterestFrequency getCompoundingFrequency() { return compoundingFrequency; } public void setCompoundingFrequency(InterestFrequency value) { compoundingFrequency = value; }
        public DayCountConvention getDayCountConvention() { return dayCountConvention; } public void setDayCountConvention(DayCountConvention value) { dayCountConvention = value; }
        public Integer getMinimumHoldingDays() { return minimumHoldingDays; } public void setMinimumHoldingDays(Integer value) { minimumHoldingDays = value; }
        public boolean isPartialWithdrawalAllowed() { return partialWithdrawalAllowed; } public void setPartialWithdrawalAllowed(boolean value) { partialWithdrawalAllowed = value; }
        public boolean isAutoRenewalAllowed() { return autoRenewalAllowed; } public void setAutoRenewalAllowed(boolean value) { autoRenewalAllowed = value; }
        public String getDefaultMaturityInstruction() { return defaultMaturityInstruction; } public void setDefaultMaturityInstruction(String value) { defaultMaturityInstruction = value; }
        public Integer getMaximumRenewalCount() { return maximumRenewalCount; } public void setMaximumRenewalCount(Integer value) { maximumRenewalCount = value; }
        public Integer getGracePeriodDays() { return gracePeriodDays; } public void setGracePeriodDays(Integer value) { gracePeriodDays = value; }
    }

    @Embeddable
    public static class FixedDepositRateSlab {
        @Column(name = "SLAB_CODE", nullable = false, length = 60) private String slabCode;
        @Column(name = "MINIMUM_TENURE", nullable = false) private Integer minimumTenure;
        @Column(name = "MAXIMUM_TENURE", nullable = false) private Integer maximumTenure;
        @Column(name = "TENURE_UNIT", nullable = false, length = 20) private String tenureUnit;
        @Column(name = "MINIMUM_AMOUNT", nullable = false, precision = 19, scale = 4) private BigDecimal minimumAmount;
        @Column(name = "MAXIMUM_AMOUNT", precision = 19, scale = 4) private BigDecimal maximumAmount;
        @Enumerated(EnumType.STRING) @Column(name = "CUSTOMER_CATEGORY", length = 30) private CustomerCategory customerCategory;
        @Column(name = "ANNUAL_INTEREST_RATE", nullable = false, precision = 9, scale = 4) private BigDecimal annualInterestRate;
        @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
        @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE", nullable = false, columnDefinition = "NUMBER(1)") private boolean active;
        public String getSlabCode() { return slabCode; } public void setSlabCode(String value) { slabCode = value; }
        public Integer getMinimumTenure() { return minimumTenure; } public void setMinimumTenure(Integer value) { minimumTenure = value; }
        public Integer getMaximumTenure() { return maximumTenure; } public void setMaximumTenure(Integer value) { maximumTenure = value; }
        public String getTenureUnit() { return tenureUnit; } public void setTenureUnit(String value) { tenureUnit = value; }
        public BigDecimal getMinimumAmount() { return minimumAmount; } public void setMinimumAmount(BigDecimal value) { minimumAmount = value; }
        public BigDecimal getMaximumAmount() { return maximumAmount; } public void setMaximumAmount(BigDecimal value) { maximumAmount = value; }
        public CustomerCategory getCustomerCategory() { return customerCategory; } public void setCustomerCategory(CustomerCategory value) { customerCategory = value; }
        public BigDecimal getAnnualInterestRate() { return annualInterestRate; } public void setAnnualInterestRate(BigDecimal value) { annualInterestRate = value; }
        public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
        public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
        public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    }

    @Embeddable
    public static class AccountClosureRule {
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "CLOSURE_ALLOWED", nullable = false, columnDefinition = "NUMBER(1)") private boolean closureAllowed;
        @Column(name = "CLOSURE_MODE", length = 50) private String closureMode;
        @Column(name = "MIN_ACCOUNT_AGE_DAYS") private Integer minimumAccountAgeDays;
        @Column(name = "NOTICE_PERIOD_DAYS") private Integer noticePeriodDays;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "CLOSURE_FEE_APPL", nullable = false, columnDefinition = "NUMBER(1)") private boolean closureFeeApplicable;
        @Column(name = "CLOSURE_FEE_CODE", length = 60) private String closureFeeCode;
        @Column(name = "CLOSURE_FEE_WAIVER_DAYS") private Integer closureFeeWaiverAfterDays;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ZERO_BALANCE_REQUIRED", nullable = false, columnDefinition = "NUMBER(1)") private boolean zeroBalanceRequired;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE_RESERVATIONS_OK", nullable = false, columnDefinition = "NUMBER(1)") private boolean activeReservationsAllowed;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "ACTIVE_MANDATES_OK", nullable = false, columnDefinition = "NUMBER(1)") private boolean activeMandatesAllowed;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "NEGATIVE_BALANCE_OK", nullable = false, columnDefinition = "NUMBER(1)") private boolean negativeBalanceAllowed;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "PENDING_CHARGES_SETTLED", nullable = false, columnDefinition = "NUMBER(1)") private boolean pendingChargesMustBeSettled;
        @Column(name = "ALLOWED_CLOSURE_CHANNELS", length = 500) private String allowedClosureChannels;
        @Column(name = "CLOSURE_SETTLEMENT_METHODS", length = 500) private String settlementMethods;
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "CLOSURE_APPROVAL_REQUIRED", nullable = false, columnDefinition = "NUMBER(1)") private boolean approvalRequired;
        @Column(name = "CLOSURE_EFFECTIVE_FROM") private LocalDate effectiveFrom;
        @Column(name = "CLOSURE_EFFECTIVE_TO") private LocalDate effectiveTo;
        @Column(name = "CLOSURE_POLICY_VERSION", length = 40) private String policyVersion;
        public boolean isClosureAllowed() { return closureAllowed; } public void setClosureAllowed(boolean value) { closureAllowed = value; }
        public String getClosureMode() { return closureMode; } public void setClosureMode(String value) { closureMode = value; }
        public Integer getMinimumAccountAgeDays() { return minimumAccountAgeDays; } public void setMinimumAccountAgeDays(Integer value) { minimumAccountAgeDays = value; }
        public Integer getNoticePeriodDays() { return noticePeriodDays; } public void setNoticePeriodDays(Integer value) { noticePeriodDays = value; }
        public boolean isClosureFeeApplicable() { return closureFeeApplicable; } public void setClosureFeeApplicable(boolean value) { closureFeeApplicable = value; }
        public String getClosureFeeCode() { return closureFeeCode; } public void setClosureFeeCode(String value) { closureFeeCode = value; }
        public Integer getClosureFeeWaiverAfterDays() { return closureFeeWaiverAfterDays; } public void setClosureFeeWaiverAfterDays(Integer value) { closureFeeWaiverAfterDays = value; }
        public boolean isZeroBalanceRequired() { return zeroBalanceRequired; } public void setZeroBalanceRequired(boolean value) { zeroBalanceRequired = value; }
        public boolean isActiveReservationsAllowed() { return activeReservationsAllowed; } public void setActiveReservationsAllowed(boolean value) { activeReservationsAllowed = value; }
        public boolean isActiveMandatesAllowed() { return activeMandatesAllowed; } public void setActiveMandatesAllowed(boolean value) { activeMandatesAllowed = value; }
        public boolean isNegativeBalanceAllowed() { return negativeBalanceAllowed; } public void setNegativeBalanceAllowed(boolean value) { negativeBalanceAllowed = value; }
        public boolean isPendingChargesMustBeSettled() { return pendingChargesMustBeSettled; } public void setPendingChargesMustBeSettled(boolean value) { pendingChargesMustBeSettled = value; }
        public String getAllowedClosureChannels() { return allowedClosureChannels; } public void setAllowedClosureChannels(String value) { allowedClosureChannels = value; }
        public String getSettlementMethods() { return settlementMethods; } public void setSettlementMethods(String value) { settlementMethods = value; }
        public boolean isApprovalRequired() { return approvalRequired; } public void setApprovalRequired(boolean value) { approvalRequired = value; }
        public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
        public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
        public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String value) { policyVersion = value; }
    }

    @Embeddable
    public static class PrematureClosureRule {
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "PREMATURE_ALLOWED", nullable = false, columnDefinition = "NUMBER(1)") private boolean allowed;
        @Column(name = "PREMATURE_MIN_HOLD_DAYS") private Integer minimumHoldingDays;
        @Column(name = "PREMATURE_CALC_METHOD", length = 60) private String calculationMethod;
        @Column(name = "PREMATURE_PENALTY_RATE", precision = 9, scale = 4) private BigDecimal penaltyRate;
        @Column(name = "MIN_PAYABLE_INTEREST_RATE", precision = 9, scale = 4) private BigDecimal minimumPayableInterestRate;
        @Column(name = "PRINCIPAL_PENALTY_PCT", precision = 9, scale = 4) private BigDecimal principalPenaltyPercentage;
        @Column(name = "APPROVAL_ABOVE_AMOUNT", precision = 19, scale = 4) private BigDecimal approvalRequiredAboveAmount;
        @Column(name = "PREMATURE_ALLOWED_CHANNELS", length = 500) private String allowedChannels;
        @Column(name = "PREMATURE_EFFECTIVE_FROM") private LocalDate effectiveFrom;
        @Column(name = "PREMATURE_EFFECTIVE_TO") private LocalDate effectiveTo;
        @Column(name = "PREMATURE_POLICY_VERSION", length = 40) private String policyVersion;
        public boolean isAllowed() { return allowed; } public void setAllowed(boolean value) { allowed = value; }
        public Integer getMinimumHoldingDays() { return minimumHoldingDays; } public void setMinimumHoldingDays(Integer value) { minimumHoldingDays = value; }
        public String getCalculationMethod() { return calculationMethod; } public void setCalculationMethod(String value) { calculationMethod = value; }
        public BigDecimal getPenaltyRate() { return penaltyRate; } public void setPenaltyRate(BigDecimal value) { penaltyRate = value; }
        public BigDecimal getMinimumPayableInterestRate() { return minimumPayableInterestRate; } public void setMinimumPayableInterestRate(BigDecimal value) { minimumPayableInterestRate = value; }
        public BigDecimal getPrincipalPenaltyPercentage() { return principalPenaltyPercentage; } public void setPrincipalPenaltyPercentage(BigDecimal value) { principalPenaltyPercentage = value; }
        public BigDecimal getApprovalRequiredAboveAmount() { return approvalRequiredAboveAmount; } public void setApprovalRequiredAboveAmount(BigDecimal value) { approvalRequiredAboveAmount = value; }
        public String getAllowedChannels() { return allowedChannels; } public void setAllowedChannels(String value) { allowedChannels = value; }
        public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
        public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
        public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String value) { policyVersion = value; }
    }

    @Embeddable
    public static class RenewalRule {
        @Convert(converter = NumericBooleanConverter.class)
        @Column(name = "RENEWAL_ALLOWED", nullable = false, columnDefinition = "NUMBER(1)") private boolean autoRenewalAllowed;
        @Column(name = "RENEWAL_OPTIONS", length = 300) private String renewalOptions;
        @Column(name = "RENEWAL_RATE_APPLICATION", length = 40) private String rateApplication;
        @Column(name = "RENEWAL_MAX_COUNT") private Integer maximumRenewalCount;
        @Column(name = "RENEWAL_POLICY_VERSION", length = 40) private String policyVersion;
        public boolean isAutoRenewalAllowed() { return autoRenewalAllowed; } public void setAutoRenewalAllowed(boolean value) { autoRenewalAllowed = value; }
        public String getRenewalOptions() { return renewalOptions; } public void setRenewalOptions(String value) { renewalOptions = value; }
        public String getRateApplication() { return rateApplication; } public void setRateApplication(String value) { rateApplication = value; }
        public Integer getMaximumRenewalCount() { return maximumRenewalCount; } public void setMaximumRenewalCount(Integer value) { maximumRenewalCount = value; }
        public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String value) { policyVersion = value; }
    }
}
