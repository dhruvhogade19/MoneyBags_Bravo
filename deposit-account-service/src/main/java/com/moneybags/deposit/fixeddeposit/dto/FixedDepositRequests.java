package com.moneybags.deposit.fixeddeposit.dto;

import com.moneybags.deposit.domain.DomainTypes.*;
import com.moneybags.deposit.dto.AccountRequests.NomineeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FixedDepositRequests {
    private FixedDepositRequests() {}
    public record QuoteRequest(@NotBlank String customerId, @NotBlank String productCode,
        @NotNull @Positive Long productVersion, @NotNull @DecimalMin("0.01") BigDecimal principal,
        @NotBlank @Pattern(regexp="[A-Z]{3}") String currency, @Min(1) int tenureValue,
        @NotNull TenureUnit tenureUnit, @NotNull InterestPayoutFrequency interestPayoutFrequency,
        @NotNull LocalDate valueDate) {}

    public record BookingRequest(@NotEmpty List<@NotBlank String> customerIds, @NotBlank String primaryCustomerId,
        @NotBlank String productCode, @NotNull @Positive Long productVersion,
        @NotNull @DecimalMin("0.01") BigDecimal principal, @NotBlank @Pattern(regexp="[A-Z]{3}") String currency,
        @Min(1) int tenureValue, @NotNull TenureUnit tenureUnit,
        @NotNull InterestPayoutFrequency interestPayoutFrequency, LocalDate valueDate, @NotBlank String fundingAccountId,
        @NotBlank String payoutAccountId, @NotBlank String servicingBranchId,
        List<@Valid NomineeRequest> nominees, @NotBlank String channel, @Size(max=64) String externalReference) {}

    public record EodRequest(@NotBlank @Size(max=64) String eodRunId, @NotNull LocalDate businessDate,
                             @NotBlank @Size(max=100) String commandReference) {}
}
