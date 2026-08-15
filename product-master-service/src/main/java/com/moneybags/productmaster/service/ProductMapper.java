package com.moneybags.productmaster.service;

import com.moneybags.productmaster.api.ProductDtos.*;
import com.moneybags.productmaster.domain.Enums.*;
import com.moneybags.productmaster.entity.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
final class ProductMapper {
    void apply(DepositProduct product, ProductRequest request, boolean creating) {
        if (creating) product.setProductCode(request.productCode().toUpperCase());
        product.setProductName(request.productName()); product.setDescription(request.description());
        product.setSubtype(request.subtype()); product.setCurrencyCode(defaultCurrency(request.currencyCode()));
        product.setEffectiveFrom(request.effectiveFrom()); product.setEffectiveTo(request.effectiveTo());
        product.setUpdatedBy(request.changedBy()); if (creating) product.setCreatedBy(request.changedBy());
        product.setAmountRule(amount(request.amountRule()));
        product.setFixedDepositRule(fixedDeposit(request.fixedDepositRule()));
        product.setAccountClosureRule(closure(request.accountClosureRule()));
        product.setPrematureClosureRule(premature(request.prematureClosureRule()));
        product.setRenewalRule(renewal(request.renewalRule()));
        replace(product.getFees(), request.fees(), this::fee);
        replace(product.getEligibilityRules(), request.eligibilityRules(), this::eligibility);
        replace(product.getFeatures(), request.features(), this::feature);
        replace(product.getInterestRateSlabs(), request.interestRateSlabs(), this::slab);
        if (request.interestRule() != null) {
            DepositInterestPolicy policy = product.getInterestPolicies().isEmpty()
                    ? new DepositInterestPolicy() : product.getInterestPolicies().getFirst();
            apply(policy, request.interestRule(), request.effectiveFrom());
            if (policy.getDepositProduct() == null) { policy.setDepositProduct(product); product.getInterestPolicies().add(policy); }
        }
    }

    void apply(CreditCardProduct product, ProductRequest request, boolean creating) {
        if (creating) product.setProductCode(request.productCode().toUpperCase());
        product.setProductName(request.productName()); product.setDescription(request.description());
        product.setCurrencyCode(defaultCurrency(request.currencyCode())); product.setEffectiveFrom(request.effectiveFrom());
        product.setEffectiveTo(request.effectiveTo()); product.setUpdatedBy(request.changedBy());
        if (creating) product.setCreatedBy(request.changedBy());
        replace(product.getFees(), request.fees(), this::fee);
        replace(product.getEligibilityRules(), request.eligibilityRules(), this::eligibility);
        replace(product.getFeatures(), request.features(), this::feature);
        if (request.interestRule() != null) {
            CreditCardInterestPolicy policy = product.getInterestPolicies().isEmpty()
                    ? new CreditCardInterestPolicy() : product.getInterestPolicies().getFirst();
            apply(policy, request.interestRule(), request.effectiveFrom());
            if (policy.getCreditCardProduct() == null) { policy.setCreditCardProduct(product); product.getInterestPolicies().add(policy); }
        }
        if (request.creditCardRule() != null) {
            CreditCardTerms terms = product.getTerms().isEmpty() ? new CreditCardTerms() : product.getTerms().getFirst();
            apply(terms, request.creditCardRule(), request.effectiveFrom());
            if (terms.getCreditCardProduct() == null) { terms.setCreditCardProduct(product); product.getTerms().add(terms); }
        }
    }

    ProductResponse response(DepositProduct product) {
        boolean fd = product.getSubtype() == Subtype.FIXED_DEPOSIT;
        return new ProductResponse(product.getProductCode(), product.getProductName(), product.getDescription(),
                Category.DEPOSIT, product.getSubtype(), product.getCurrencyCode(), product.getStatus(),
                product.getEffectiveFrom(), product.getEffectiveTo(), product.getCreatedAt(), product.getUpdatedAt(),
                product.getCreatedBy(), product.getUpdatedBy(), 1L, interest(effective(product.getInterestPolicies(), LocalDate.now())),
                amount(product.getAmountRule()), fd ? fixedDeposit(product.getFixedDepositRule()) : null,
                fd ? product.getInterestRateSlabs().stream().map(this::slab).toList() : List.of(),
                closure(product.getAccountClosureRule()), fd ? premature(product.getPrematureClosureRule()) : null,
                fd ? renewal(product.getRenewalRule()) : null, null, product.getFees().stream().map(this::fee).toList(),
                product.getEligibilityRules().stream().map(this::eligibility).toList(), product.getFeatures().stream().map(this::feature).toList());
    }

    ProductResponse response(CreditCardProduct product) {
        return new ProductResponse(product.getProductCode(), product.getProductName(), product.getDescription(),
                Category.CREDIT_CARD, Subtype.CREDIT_CARD, product.getCurrencyCode(), product.getStatus(),
                product.getEffectiveFrom(), product.getEffectiveTo(), product.getCreatedAt(), product.getUpdatedAt(),
                product.getCreatedBy(), product.getUpdatedBy(), 1L, interest(effective(product.getInterestPolicies(), LocalDate.now())),
                null, null, List.of(), null, null, null, card(effectiveTerms(product.getTerms(), LocalDate.now())),
                product.getFees().stream().map(this::fee).toList(), product.getEligibilityRules().stream().map(this::eligibility).toList(),
                product.getFeatures().stream().map(this::feature).toList());
    }

    InterestRuleDto interest(AbstractInterestPolicy policy) {
        if (policy == null) return null;
        return new InterestRuleDto(policy.getAnnualInterestRate(), policy.getPricingMode(), policy.getBenchmarkCode(),
                policy.getProductSpread(), policy.getMinimumRate(), policy.getMaximumRate(), policy.getTargetProfitPercentage(),
                policy.getEffectiveFrom(), policy.getEffectiveTo(), policy.getPolicyVersion(), policy.getInterestCalculationMethod(),
                policy.getInterestCalculationFrequency(), policy.getInterestPostingFrequency(), policy.getCompoundingFrequency(),
                policy.getDayCountConvention(), policy.getRateApplicationMethod(), null, policy.getInterestType());
    }

    AmountRuleDto amount(CatalogRuleValues.DepositAmountRule value) {
        if (value == null) return null;
        return new AmountRuleDto(value.getMinimumOpeningBalance(), value.getMinimumBalance(), value.getMaximumBalance(),
                value.getMinimumAmount(), value.getMaximumAmount(), value.getMinimumTenureMonths(), value.getMaximumTenureMonths(),
                value.isOverdraftAllowed(), value.getOverdraftLimit());
    }

    CatalogRuleValues.DepositAmountRule amount(AmountRuleDto value) {
        if (value == null) return new CatalogRuleValues.DepositAmountRule();
        CatalogRuleValues.DepositAmountRule result = new CatalogRuleValues.DepositAmountRule();
        result.setMinimumOpeningBalance(value.minimumOpeningBalance()); result.setMinimumBalance(value.minimumBalance());
        result.setMaximumBalance(value.maximumBalance()); result.setMinimumAmount(value.minimumAmount()); result.setMaximumAmount(value.maximumAmount());
        result.setMinimumTenureMonths(value.minimumTenureMonths()); result.setMaximumTenureMonths(value.maximumTenureMonths());
        result.setOverdraftAllowed(value.overdraftAllowed()); result.setOverdraftLimit(value.overdraftLimit()); return result;
    }

    FixedDepositRuleDto fixedDeposit(CatalogRuleValues.FixedDepositRule value) {
        if (value == null || value.getDefaultInterestPayoutFrequency() == null) return null;
        return new FixedDepositRuleDto(csv(value.getAllowedTenureUnits()), csvEnums(value.getAllowedInterestPayoutFrequencies()),
                value.getDefaultInterestPayoutFrequency(), value.getCompoundingFrequency(), value.getDayCountConvention(),
                value.getMinimumHoldingDays(), value.isPartialWithdrawalAllowed(), value.isAutoRenewalAllowed(),
                value.getDefaultMaturityInstruction(), value.getMaximumRenewalCount(), value.getGracePeriodDays());
    }

    CatalogRuleValues.FixedDepositRule fixedDeposit(FixedDepositRuleDto value) {
        CatalogRuleValues.FixedDepositRule result = new CatalogRuleValues.FixedDepositRule(); if (value == null) return result;
        result.setAllowedTenureUnits(join(value.allowedTenureUnits())); result.setAllowedInterestPayoutFrequencies(join(value.allowedInterestPayoutFrequencies()));
        result.setDefaultInterestPayoutFrequency(value.defaultInterestPayoutFrequency()); result.setCompoundingFrequency(value.compoundingFrequency());
        result.setDayCountConvention(value.dayCountConvention()); result.setMinimumHoldingDays(value.minimumHoldingDays());
        result.setPartialWithdrawalAllowed(value.partialWithdrawalAllowed()); result.setAutoRenewalAllowed(value.autoRenewalAllowed());
        result.setDefaultMaturityInstruction(value.defaultMaturityInstruction()); result.setMaximumRenewalCount(value.maximumRenewalCount()); result.setGracePeriodDays(value.gracePeriodDays()); return result;
    }

    InterestRateSlabDto slab(CatalogRuleValues.FixedDepositRateSlab value) {
        return new InterestRateSlabDto(value.getSlabCode(), value.getMinimumTenure(), value.getMaximumTenure(), value.getTenureUnit(),
                value.getMinimumAmount(), value.getMaximumAmount(), value.getCustomerCategory(), value.getAnnualInterestRate(),
                value.getEffectiveFrom(), value.getEffectiveTo(), value.isActive());
    }
    CatalogRuleValues.FixedDepositRateSlab slab(InterestRateSlabDto value) {
        CatalogRuleValues.FixedDepositRateSlab result = new CatalogRuleValues.FixedDepositRateSlab(); result.setSlabCode(value.slabCode());
        result.setMinimumTenure(value.minimumTenure()); result.setMaximumTenure(value.maximumTenure()); result.setTenureUnit(value.tenureUnit());
        result.setMinimumAmount(value.minimumAmount()); result.setMaximumAmount(value.maximumAmount()); result.setCustomerCategory(value.customerCategory());
        result.setAnnualInterestRate(value.annualInterestRate()); result.setEffectiveFrom(value.effectiveFrom()); result.setEffectiveTo(value.effectiveTo()); result.setActive(value.active()); return result;
    }

    AccountClosureRuleDto closure(CatalogRuleValues.AccountClosureRule value) {
        if (value == null || value.getClosureMode() == null) return null;
        return new AccountClosureRuleDto(value.isClosureAllowed(), value.getClosureMode(), value.getMinimumAccountAgeDays(), value.getNoticePeriodDays(),
                value.isClosureFeeApplicable(), value.getClosureFeeCode(), value.getClosureFeeWaiverAfterDays(), value.isZeroBalanceRequired(),
                value.isActiveReservationsAllowed(), value.isActiveMandatesAllowed(), value.isNegativeBalanceAllowed(), value.isPendingChargesMustBeSettled(),
                csv(value.getAllowedClosureChannels()), csv(value.getSettlementMethods()), value.isApprovalRequired(), value.getEffectiveFrom(), value.getEffectiveTo(), value.getPolicyVersion());
    }
    CatalogRuleValues.AccountClosureRule closure(AccountClosureRuleDto value) {
        CatalogRuleValues.AccountClosureRule result = new CatalogRuleValues.AccountClosureRule(); if (value == null) return result;
        result.setClosureAllowed(value.closureAllowed()); result.setClosureMode(value.closureMode()); result.setMinimumAccountAgeDays(value.minimumAccountAgeDays()); result.setNoticePeriodDays(value.noticePeriodDays());
        result.setClosureFeeApplicable(value.closureFeeApplicable()); result.setClosureFeeCode(value.closureFeeCode()); result.setClosureFeeWaiverAfterDays(value.closureFeeWaiverAfterDays()); result.setZeroBalanceRequired(value.zeroBalanceRequired());
        result.setActiveReservationsAllowed(value.activeReservationsAllowed()); result.setActiveMandatesAllowed(value.activeMandatesAllowed()); result.setNegativeBalanceAllowed(value.negativeBalanceAllowed()); result.setPendingChargesMustBeSettled(value.pendingChargesMustBeSettled());
        result.setAllowedClosureChannels(join(value.allowedClosureChannels())); result.setSettlementMethods(join(value.settlementMethods())); result.setApprovalRequired(value.approvalRequired()); result.setEffectiveFrom(value.effectiveFrom()); result.setEffectiveTo(value.effectiveTo()); result.setPolicyVersion(value.policyVersion()); return result;
    }

    PrematureClosureRuleDto premature(CatalogRuleValues.PrematureClosureRule value) {
        if (value == null || value.getCalculationMethod() == null) return null;
        return new PrematureClosureRuleDto(value.isAllowed(), value.getMinimumHoldingDays(), value.getCalculationMethod(), value.getPenaltyRate(),
                value.getMinimumPayableInterestRate(), value.getPrincipalPenaltyPercentage(), value.getApprovalRequiredAboveAmount(),
                csv(value.getAllowedChannels()), value.getEffectiveFrom(), value.getEffectiveTo(), value.getPolicyVersion());
    }
    CatalogRuleValues.PrematureClosureRule premature(PrematureClosureRuleDto value) {
        CatalogRuleValues.PrematureClosureRule result = new CatalogRuleValues.PrematureClosureRule(); if (value == null) return result;
        result.setAllowed(value.allowed()); result.setMinimumHoldingDays(value.minimumHoldingDays()); result.setCalculationMethod(value.calculationMethod()); result.setPenaltyRate(value.penaltyRate());
        result.setMinimumPayableInterestRate(value.minimumPayableInterestRate()); result.setPrincipalPenaltyPercentage(value.principalPenaltyPercentage()); result.setApprovalRequiredAboveAmount(value.approvalRequiredAboveAmount());
        result.setAllowedChannels(join(value.allowedChannels())); result.setEffectiveFrom(value.effectiveFrom()); result.setEffectiveTo(value.effectiveTo()); result.setPolicyVersion(value.policyVersion()); return result;
    }

    RenewalRuleDto renewal(CatalogRuleValues.RenewalRule value) {
        if (value == null || value.getRateApplication() == null) return null;
        return new RenewalRuleDto(value.isAutoRenewalAllowed(), csv(value.getRenewalOptions()), value.getRateApplication(), value.getMaximumRenewalCount(), value.getPolicyVersion());
    }
    CatalogRuleValues.RenewalRule renewal(RenewalRuleDto value) {
        CatalogRuleValues.RenewalRule result = new CatalogRuleValues.RenewalRule(); if (value == null) return result;
        result.setAutoRenewalAllowed(value.autoRenewalAllowed()); result.setRenewalOptions(join(value.renewalOptions())); result.setRateApplication(value.rateApplication()); result.setMaximumRenewalCount(value.maximumRenewalCount()); result.setPolicyVersion(value.policyVersion()); return result;
    }

    CreditCardRuleDto card(CreditCardTerms value) {
        if (value == null) return null;
        return new CreditCardRuleDto(value.getPolicyVersion(), value.getEffectiveFrom(), value.getEffectiveTo(), value.getMinimumCreditLimit(),
                value.getMaximumCreditLimit(), value.getInterestFreeDays(), value.getMinimumPaymentPercentage(), value.getMinimumPaymentAmount(),
                value.getPaymentDueDays(), value.isCashAdvanceAllowed(), value.getCashAdvanceLimitPercentage());
    }
    void apply(CreditCardTerms target, CreditCardRuleDto value, LocalDate productEffectiveFrom) {
        target.setPolicyVersion("V1"); target.setEffectiveFrom(value.effectiveFrom() == null ? productEffectiveFrom : value.effectiveFrom()); target.setEffectiveTo(value.effectiveTo());
        target.setMinimumCreditLimit(value.minimumCreditLimit()); target.setMaximumCreditLimit(value.maximumCreditLimit()); target.setInterestFreeDays(value.interestFreeDays());
        target.setMinimumPaymentPercentage(value.minimumPaymentPercentage()); target.setMinimumPaymentAmount(value.minimumPaymentAmount()); target.setPaymentDueDays(value.paymentDueDays());
        target.setCashAdvanceAllowed(value.cashAdvanceAllowed()); target.setCashAdvanceLimitPercentage(value.cashAdvanceLimitPercentage());
    }

    FeeDto fee(CatalogRuleValues.Fee value) { return new FeeDto(value.getFeeType(), value.getFeeAmount(), value.getFeePercentage(), value.getFrequency(), value.isActive()); }
    CatalogRuleValues.Fee fee(FeeDto value) { CatalogRuleValues.Fee result = new CatalogRuleValues.Fee(); result.setFeeType(value.feeType()); result.setFeeAmount(value.feeAmount()); result.setFeePercentage(value.feePercentage()); result.setFrequency(value.frequency()); result.setActive(value.active()); return result; }
    EligibilityRuleDto eligibility(CatalogRuleValues.Eligibility value) { return new EligibilityRuleDto(value.getMinimumAge(), value.getMaximumAge(), value.getMinimumMonthlyIncome(), value.getCustomerType(), value.getCustomerCategory(), value.isKycRequired(), value.isCollateralRequired(), value.isActive()); }
    CatalogRuleValues.Eligibility eligibility(EligibilityRuleDto value) { CatalogRuleValues.Eligibility result = new CatalogRuleValues.Eligibility(); result.setMinimumAge(value.minimumAge()); result.setMaximumAge(value.maximumAge()); result.setMinimumMonthlyIncome(value.minimumMonthlyIncome()); result.setCustomerType(value.customerType()); result.setCustomerCategory(value.customerCategory()); result.setKycRequired(value.kycRequired()); result.setCollateralRequired(value.collateralRequired()); result.setActive(value.active()); return result; }
    FeatureDto feature(CatalogRuleValues.Feature value) { return new FeatureDto(value.getFeatureName(), value.getFeatureValue(), value.isActive()); }
    CatalogRuleValues.Feature feature(FeatureDto value) { CatalogRuleValues.Feature result = new CatalogRuleValues.Feature(); result.setFeatureName(value.featureName()); result.setFeatureValue(value.featureValue()); result.setActive(value.active()); return result; }

    void apply(AbstractInterestPolicy target, InterestRuleDto value, LocalDate productEffectiveFrom) {
        target.setAnnualInterestRate(value.annualInterestRate()); target.setPricingMode(value.pricingMode() == null ? PricingMode.FIXED : value.pricingMode());
        target.setBenchmarkCode(value.benchmarkCode() == null ? null : value.benchmarkCode().toUpperCase()); target.setProductSpread(value.productSpread());
        target.setMinimumRate(value.minimumRate()); target.setMaximumRate(value.maximumRate()); target.setTargetProfitPercentage(value.targetProfitPercentage());
        target.setEffectiveFrom(value.effectiveFrom() == null ? productEffectiveFrom : value.effectiveFrom()); target.setEffectiveTo(value.effectiveTo()); target.setPolicyVersion("V1");
        target.setInterestCalculationMethod(value.interestCalculationMethod()); target.setInterestCalculationFrequency(value.interestCalculationFrequency()); target.setInterestPostingFrequency(value.interestPostingFrequency());
        target.setCompoundingFrequency(value.compoundingFrequency()); target.setDayCountConvention(value.dayCountConvention()); target.setRateApplicationMethod(value.rateApplicationMethod()); target.setInterestType(value.interestType());
    }

    <T extends AbstractInterestPolicy> T effective(List<T> values, LocalDate date) { return values.stream().filter(v -> isEffective(v.getEffectiveFrom(), v.getEffectiveTo(), date)).findFirst().orElse(values.isEmpty() ? null : values.getFirst()); }
    CreditCardTerms effectiveTerms(List<CreditCardTerms> values, LocalDate date) { return values.stream().filter(v -> isEffective(v.getEffectiveFrom(), v.getEffectiveTo(), date)).findFirst().orElse(values.isEmpty() ? null : values.getFirst()); }
    boolean isEffective(LocalDate from, LocalDate to, LocalDate date) { return from != null && !from.isAfter(date) && (to == null || !to.isBefore(date)); }
    private <S, T> void replace(List<T> target, List<S> source, Function<S, T> mapper) { target.clear(); if (source != null) target.addAll(source.stream().map(mapper).toList()); }
    private String defaultCurrency(String value) { return value == null ? "INR" : value; }
    private String join(List<?> values) { return values == null || values.isEmpty() ? null : values.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse(null); }
    private List<String> csv(String value) { return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(",")); }
    private List<InterestPostingFrequency> csvEnums(String value) { return csv(value).stream().map(InterestPostingFrequency::valueOf).toList(); }
}
