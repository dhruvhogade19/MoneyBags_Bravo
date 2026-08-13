package com.moneybags.productmaster.entity;

import com.moneybags.productmaster.domain.Enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@MappedSuperclass
public abstract class AbstractInterestPolicy {
    @Id @Column(name = "INTEREST_POLICY_ID", length = 36) private String id;
    @Column(name = "ANNUAL_INTEREST_RATE", precision = 9, scale = 4) private BigDecimal annualInterestRate;
    @Enumerated(EnumType.STRING) @Column(name = "PRICING_MODE", nullable = false, length = 30) private PricingMode pricingMode = PricingMode.FIXED;
    @Column(name = "BENCHMARK_CODE", length = 40) private String benchmarkCode;
    @Column(name = "PRODUCT_SPREAD", precision = 9, scale = 4) private BigDecimal productSpread;
    @Column(name = "MINIMUM_RATE", precision = 9, scale = 4) private BigDecimal minimumRate;
    @Column(name = "MAXIMUM_RATE", precision = 9, scale = 4) private BigDecimal maximumRate;
    @Column(name = "TARGET_PROFIT_PERCENTAGE", precision = 9, scale = 4) private BigDecimal targetProfitPercentage;
    @Column(name = "EFFECTIVE_FROM", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "EFFECTIVE_TO") private LocalDate effectiveTo;
    @Column(name = "POLICY_VERSION", nullable = false, length = 40) private String policyVersion = "V1";
    @Enumerated(EnumType.STRING) @Column(name = "CALCULATION_METHOD", nullable = false, length = 30) private InterestCalculationMethod interestCalculationMethod;
    @Enumerated(EnumType.STRING) @Column(name = "CALCULATION_FREQUENCY", nullable = false, length = 20) private InterestFrequency interestCalculationFrequency;
    @Enumerated(EnumType.STRING) @Column(name = "POSTING_FREQUENCY", nullable = false, length = 20) private InterestPostingFrequency interestPostingFrequency;
    @Enumerated(EnumType.STRING) @Column(name = "COMPOUNDING_FREQUENCY", length = 20) private InterestFrequency compoundingFrequency;
    @Enumerated(EnumType.STRING) @Column(name = "DAY_COUNT_CONVENTION", length = 20) private DayCountConvention dayCountConvention;
    @Enumerated(EnumType.STRING) @Column(name = "RATE_APPLICATION_METHOD", length = 30) private RateApplicationMethod rateApplicationMethod;
    @Enumerated(EnumType.STRING) @Column(name = "INTEREST_TYPE", nullable = false, length = 10) private InterestType interestType;
    @PrePersist void onCreate() { if (id == null) id = UUID.randomUUID().toString(); policyVersion = "V1"; }
    public String getId() { return id; } public BigDecimal getAnnualInterestRate() { return annualInterestRate; } public void setAnnualInterestRate(BigDecimal value) { annualInterestRate = value; }
    public PricingMode getPricingMode() { return pricingMode; } public void setPricingMode(PricingMode value) { pricingMode = value; }
    public String getBenchmarkCode() { return benchmarkCode; } public void setBenchmarkCode(String value) { benchmarkCode = value; }
    public BigDecimal getProductSpread() { return productSpread; } public void setProductSpread(BigDecimal value) { productSpread = value; }
    public BigDecimal getMinimumRate() { return minimumRate; } public void setMinimumRate(BigDecimal value) { minimumRate = value; }
    public BigDecimal getMaximumRate() { return maximumRate; } public void setMaximumRate(BigDecimal value) { maximumRate = value; }
    public BigDecimal getTargetProfitPercentage() { return targetProfitPercentage; } public void setTargetProfitPercentage(BigDecimal value) { targetProfitPercentage = value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String ignored) { policyVersion = "V1"; }
    public InterestCalculationMethod getInterestCalculationMethod() { return interestCalculationMethod; } public void setInterestCalculationMethod(InterestCalculationMethod value) { interestCalculationMethod = value; }
    public InterestFrequency getInterestCalculationFrequency() { return interestCalculationFrequency; } public void setInterestCalculationFrequency(InterestFrequency value) { interestCalculationFrequency = value; }
    public InterestPostingFrequency getInterestPostingFrequency() { return interestPostingFrequency; } public void setInterestPostingFrequency(InterestPostingFrequency value) { interestPostingFrequency = value; }
    public InterestFrequency getCompoundingFrequency() { return compoundingFrequency; } public void setCompoundingFrequency(InterestFrequency value) { compoundingFrequency = value; }
    public DayCountConvention getDayCountConvention() { return dayCountConvention; } public void setDayCountConvention(DayCountConvention value) { dayCountConvention = value; }
    public RateApplicationMethod getRateApplicationMethod() { return rateApplicationMethod; } public void setRateApplicationMethod(RateApplicationMethod value) { rateApplicationMethod = value; }
    public InterestType getInterestType() { return interestType; } public void setInterestType(InterestType value) { interestType = value; }
}
