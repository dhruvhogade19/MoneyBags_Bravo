package com.moneybags.productmaster.api;

import com.moneybags.productmaster.domain.Enums.PricingMode;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PricingDtos {
    private PricingDtos() {}

    public record BenchmarkRateRequest(
            @NotBlank @Pattern(regexp = "[A-Z0-9_-]+") String benchmarkCode,
            @NotNull @DecimalMin("0.0") BigDecimal annualRate,
            @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
            @NotBlank String createdBy) {}

    public record BenchmarkRateResponse(
            Long id, String benchmarkCode, BigDecimal annualRate,
            LocalDate effectiveFrom, LocalDate effectiveTo,
            Instant createdAt, String createdBy) {}

    public record RateQuoteResponse(
            String productCode, LocalDate quoteDate, PricingMode pricingMode,
            BigDecimal offeredAnnualRate, BigDecimal fixedRate, String benchmarkCode,
            BigDecimal benchmarkRate, BigDecimal productSpread, BigDecimal minimumRate,
            BigDecimal maximumRate, BigDecimal targetProfitPercentage, BigDecimal estimatedNpv,
            String policyVersion, List<String> calculationSteps) {}
}
