package com.moneybags.productmaster.service;

import com.moneybags.productmaster.api.PricingDtos.RateQuoteResponse;
import com.moneybags.productmaster.api.ProductDtos.*;
import com.moneybags.productmaster.domain.Enums.*;
import com.moneybags.productmaster.entity.BenchmarkRate;
import com.moneybags.productmaster.exception.ProductExceptions.BusinessValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PricingService {
    private final ProductService products;
    private final BenchmarkRateService benchmarks;
    public PricingService(ProductService products, BenchmarkRateService benchmarks) { this.products = products; this.benchmarks = benchmarks; }

    @Transactional(readOnly = true)
    public RateQuoteResponse getQuote(String productCode, LocalDate quoteDate, BigDecimal principal, Integer tenureMonths) {
        ProductResponse product = products.get(productCode);
        if (product.status() != Status.ACTIVE || product.effectiveFrom().isAfter(quoteDate) || product.effectiveTo() != null && product.effectiveTo().isBefore(quoteDate)) fail("Product is not active and effective on " + quoteDate);
        InterestRuleDto policy = products.interestPolicies(productCode).stream().filter(rule -> !rule.effectiveFrom().isAfter(quoteDate) && (rule.effectiveTo() == null || !rule.effectiveTo().isBefore(quoteDate))).findFirst().orElseThrow(() -> new BusinessValidationException(List.of("No interest policy is effective for " + productCode + " on " + quoteDate)));
        if (product.subtype() == Subtype.FIXED_DEPOSIT && principal != null && tenureMonths != null) {
            InterestRateSlabDto slab = product.interestRateSlabs().stream().filter(value -> value.active() && "MONTH".equals(value.tenureUnit()) && tenureMonths >= value.minimumTenure() && tenureMonths <= value.maximumTenure() && principal.compareTo(value.minimumAmount()) >= 0 && (value.maximumAmount() == null || principal.compareTo(value.maximumAmount()) <= 0) && !value.effectiveFrom().isAfter(quoteDate) && (value.effectiveTo() == null || !value.effectiveTo().isBefore(quoteDate))).findFirst().orElseThrow(() -> new BusinessValidationException(List.of("No fixed-deposit rate slab matches the quote inputs")));
            return response(productCode, quoteDate, policy, slab.annualInterestRate(), slab.annualInterestRate(), null, null, List.of("Matched fixed-deposit slab " + slab.slabCode(), "Final offered annual rate = " + percent(slab.annualInterestRate())));
        }
        if (policy.pricingMode() == PricingMode.FIXED) { BigDecimal offered = bounded(policy.annualInterestRate(), policy); return response(productCode, quoteDate, policy, offered, policy.annualInterestRate(), null, null, List.of("Fixed policy rate = " + percent(policy.annualInterestRate()), "Final offered annual rate = " + percent(offered))); }
        BenchmarkRate benchmark = benchmarks.effective(policy.benchmarkCode(), quoteDate); BigDecimal calculated = benchmark.getAnnualRate().add(policy.productSpread()); BigDecimal offered = bounded(calculated, policy); List<String> steps = new ArrayList<>(); steps.add(policy.benchmarkCode() + " benchmark = " + percent(benchmark.getAnnualRate())); steps.add("Product spread = " + percent(policy.productSpread())); steps.add("Benchmark + spread = " + percent(calculated)); if (calculated.compareTo(offered) != 0) steps.add("Configured rate floor/cap applied"); steps.add("Final offered annual rate = " + percent(offered)); return response(productCode, quoteDate, policy, offered, null, benchmark.getBenchmarkCode(), benchmark.getAnnualRate(), steps);
    }

    public InterestRuleDto addPolicy(String productCode, InterestRuleDto request) { return products.addInterestPolicy(productCode, request); }
    @Transactional(readOnly = true) public List<InterestRuleDto> policies(String productCode) { return products.interestPolicies(productCode); }
    private BigDecimal bounded(BigDecimal calculated, InterestRuleDto policy) { BigDecimal result = calculated; if (policy.minimumRate() != null && result.compareTo(policy.minimumRate()) < 0) result = policy.minimumRate(); if (policy.maximumRate() != null && result.compareTo(policy.maximumRate()) > 0) result = policy.maximumRate(); return result.setScale(4, RoundingMode.HALF_UP); }
    private RateQuoteResponse response(String code, LocalDate date, InterestRuleDto policy, BigDecimal offered, BigDecimal fixed, String benchmarkCode, BigDecimal benchmarkRate, List<String> steps) { return new RateQuoteResponse(code, date, policy.pricingMode(), offered, fixed, benchmarkCode, benchmarkRate, policy.productSpread(), policy.minimumRate(), policy.maximumRate(), policy.targetProfitPercentage(), null, policy.policyVersion(), steps); }
    private String percent(BigDecimal value) { return value.stripTrailingZeros().toPlainString() + "%"; }
    private void fail(String message) { throw new BusinessValidationException(List.of(message)); }
}
