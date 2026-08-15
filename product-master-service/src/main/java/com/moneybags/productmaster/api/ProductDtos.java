package com.moneybags.productmaster.api;

import com.moneybags.productmaster.domain.Enums.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]+") String productCode,
            @NotBlank @Size(max = 120) String productName,
            @Size(max = 1000) String description,
            @NotNull Category category, @NotNull Subtype subtype,
            @Pattern(regexp = "[A-Z]{3}") String currencyCode,
            @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
            @NotBlank String changedBy, @Valid InterestRuleDto interestRule,
            @Valid AmountRuleDto amountRule, @Valid CreditCardRuleDto creditCardRule,
            @Valid FixedDepositRuleDto fixedDepositRule,
            @Valid List<InterestRateSlabDto> interestRateSlabs,
            @Valid AccountClosureRuleDto accountClosureRule,
            @Valid PrematureClosureRuleDto prematureClosureRule,
            @Valid RenewalRuleDto renewalRule,
            @Valid List<FeeDto> fees, @Valid List<EligibilityRuleDto> eligibilityRules,
            @Valid List<FeatureDto> features) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InterestRuleDto(
            @DecimalMin("0.0") BigDecimal annualInterestRate, PricingMode pricingMode,
            @Pattern(regexp = "[A-Z0-9_-]+") String benchmarkCode,
            @DecimalMin("0.0") BigDecimal productSpread,
            @DecimalMin("0.0") BigDecimal minimumRate,
            @DecimalMin("0.0") BigDecimal maximumRate,
            @DecimalMin("0.0") BigDecimal targetProfitPercentage,
            LocalDate effectiveFrom, LocalDate effectiveTo,
            @Pattern(regexp = "[A-Z0-9._-]+") String policyVersion,
            @NotNull InterestCalculationMethod interestCalculationMethod,
            @NotNull InterestFrequency interestCalculationFrequency,
            @NotNull InterestPostingFrequency interestPostingFrequency,
            InterestFrequency compoundingFrequency,
            DayCountConvention dayCountConvention,
            RateApplicationMethod rateApplicationMethod,
            InterestFrequency loanRepaymentFrequency,
            @NotNull InterestType interestType) {}

    public record AmountRuleDto(
            @DecimalMin("0.0") BigDecimal minimumOpeningBalance,
            @DecimalMin("0.0") BigDecimal minimumBalance,
            @DecimalMin("0.0") BigDecimal maximumBalance,
            @DecimalMin("0.0") BigDecimal minimumAmount,
            @DecimalMin("0.0") BigDecimal maximumAmount,
            @Min(1) Integer minimumTenureMonths,
            @Min(1) Integer maximumTenureMonths,
            boolean overdraftAllowed,
            @DecimalMin("0.0") BigDecimal overdraftLimit) {}

    public record CreditCardRuleDto(
            @Pattern(regexp = "[A-Z0-9._-]+") String policyVersion,
            LocalDate effectiveFrom, LocalDate effectiveTo,
            @NotNull @DecimalMin("0.0") BigDecimal minimumCreditLimit,
            @NotNull @DecimalMin("0.0") BigDecimal maximumCreditLimit,
            @NotNull @Min(0) @Max(90) Integer interestFreeDays,
            @NotNull @DecimalMin("0.0001") @DecimalMax("100.0") BigDecimal minimumPaymentPercentage,
            @NotNull @DecimalMin("0.0") BigDecimal minimumPaymentAmount,
            @NotNull @Min(1) @Max(60) Integer paymentDueDays,
            boolean cashAdvanceAllowed,
            @DecimalMin("0.0001") @DecimalMax("100.0") BigDecimal cashAdvanceLimitPercentage) {}

    public record FixedDepositRuleDto(
            List<String> allowedTenureUnits,
            List<InterestPostingFrequency> allowedInterestPayoutFrequencies,
            InterestPostingFrequency defaultInterestPayoutFrequency,
            InterestFrequency compoundingFrequency,
            DayCountConvention dayCountConvention,
            @Min(0) Integer minimumHoldingDays,
            boolean partialWithdrawalAllowed,
            boolean autoRenewalAllowed,
            String defaultMaturityInstruction,
            @Min(0) Integer maximumRenewalCount,
            @Min(0) Integer gracePeriodDays) {}

    public record InterestRateSlabDto(
            @NotBlank @Pattern(regexp = "[A-Z0-9-]+") String slabCode,
            @NotNull @Min(1) Integer minimumTenure,
            @NotNull @Min(1) Integer maximumTenure,
            @NotBlank String tenureUnit,
            @NotNull @DecimalMin("0.0") BigDecimal minimumAmount,
            @DecimalMin("0.0") BigDecimal maximumAmount,
            CustomerCategory customerCategory,
            @NotNull @DecimalMin("0.0") BigDecimal annualInterestRate,
            @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
            boolean active) {}

    public record AccountClosureRuleDto(
            boolean closureAllowed, String closureMode, @Min(0) Integer minimumAccountAgeDays,
            @Min(0) Integer noticePeriodDays, boolean closureFeeApplicable,
            String closureFeeCode, @Min(0) Integer closureFeeWaiverAfterDays,
            boolean zeroBalanceRequired, boolean activeReservationsAllowed,
            boolean activeMandatesAllowed, boolean negativeBalanceAllowed,
            boolean pendingChargesMustBeSettled, List<String> allowedClosureChannels,
            List<String> settlementMethods, boolean approvalRequired,
            LocalDate effectiveFrom, LocalDate effectiveTo, String policyVersion) {}

    public record PrematureClosureRuleDto(
            boolean allowed, @Min(0) Integer minimumHoldingDays, String calculationMethod,
            @DecimalMin("0.0") BigDecimal penaltyRate,
            @DecimalMin("0.0") BigDecimal minimumPayableInterestRate,
            @DecimalMin("0.0") BigDecimal principalPenaltyPercentage,
            @DecimalMin("0.0") BigDecimal approvalRequiredAboveAmount,
            List<String> allowedChannels, LocalDate effectiveFrom, LocalDate effectiveTo,
            String policyVersion) {}

    public record RenewalRuleDto(
            boolean autoRenewalAllowed, List<String> renewalOptions, String rateApplication,
            @Min(0) Integer maximumRenewalCount, String policyVersion) {}

    public record FeeDto(
            @NotNull FeeType feeType, @DecimalMin("0.0") BigDecimal feeAmount,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal feePercentage,
            @NotNull FeeFrequency frequency, boolean active) {}

    public record EligibilityRuleDto(
            @Min(0) Integer minimumAge, @Min(0) Integer maximumAge,
            @DecimalMin("0.0") BigDecimal minimumMonthlyIncome,
            @NotNull CustomerType customerType, CustomerCategory customerCategory, boolean kycRequired,
            boolean collateralRequired, boolean active) {}

    public record FeatureDto(
            @NotBlank @Size(max = 100) String featureName,
            @Size(max = 500) String featureValue, boolean active) {}

    public record ProductResponse(
            String productCode, String productName, String description, Category category,
            Subtype subtype, String currencyCode, Status status, LocalDate effectiveFrom,
            LocalDate effectiveTo, Instant createdAt, Instant updatedAt, String createdBy,
            String updatedBy, Long version, InterestRuleDto interestRule, AmountRuleDto amountRule,
            FixedDepositRuleDto fixedDepositRule, List<InterestRateSlabDto> interestRateSlabs,
            AccountClosureRuleDto accountClosureRule,
            PrematureClosureRuleDto prematureClosureRule, RenewalRuleDto renewalRule,
            CreditCardRuleDto creditCardRule, List<FeeDto> fees,
            List<EligibilityRuleDto> eligibilityRules, List<FeatureDto> features) {}

    public record StatusRequest(@NotNull Status status, @NotBlank String changedBy) {}

    public record AccountOpeningValidationRequest(
            @NotNull @DecimalMin("0.0") BigDecimal openingAmount,
            @Min(0) Integer age, CustomerType customerType, CustomerCategory customerCategory,
            @Min(1) Integer tenureMonths, String tenureUnit,
            InterestPostingFrequency interestPayoutFrequency, Boolean kycCompleted) {}

    public record CreditCardApplicationValidationRequest(
            @NotNull @DecimalMin("0.0") BigDecimal requestedCreditLimit,
            @Min(0) Integer age, @DecimalMin("0.0") BigDecimal monthlyIncome,
            CustomerType customerType, Boolean kycCompleted) {}

    public record ValidationResponse(
            boolean eligible, List<String> validationMessages, List<FeeDto> applicableFees,
            InterestRuleDto applicableInterestRule, AmountRuleDto applicableAmountRules) {}

    public record CreditCardValidationResponse(
            boolean eligible, List<String> validationMessages, List<FeeDto> applicableFees,
            InterestRuleDto applicableInterestRule, CreditCardRuleDto applicableCreditCardRule) {}

    public record MinimalCreditCardProductResponse(
            String productCode, String productName, BigDecimal interestRate,
            EligibilityRuleDto eligibility, List<String> messages) {}

    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements,
            int totalPages, boolean first, boolean last) {}
}
