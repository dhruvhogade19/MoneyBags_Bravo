package com.moneybags.deposit.dto;

import com.moneybags.deposit.domain.DomainTypes.HolderRole;
import com.moneybags.deposit.domain.DomainTypes.LimitType;
import com.moneybags.deposit.domain.DomainTypes.OperatingInstruction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class AccountRequests {
    private AccountRequests() {}

    public record OpenDepositAccountRequest(
            @NotEmpty @Size(max = 10) List<@NotBlank String> customerIds,
            @NotBlank String primaryCustomerId,
            @NotBlank String productId,
            @NotNull @Positive Long productVersion,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin("0.00") BigDecimal openingAmount,
            @NotBlank String servicingBranchId,
            @NotNull OperatingInstruction operatingInstruction,
            List<@Valid NomineeRequest> nominees,
            @NotBlank String channel,
            @Size(max = 64) String externalReference
    ) {}

    public record EligibilityCheckRequest(
            @NotBlank String customerId,
            @NotBlank String productId,
            @NotNull @Positive Long productVersion,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DecimalMin("0.00") BigDecimal openingAmount
    ) {}

    public record HolderRequest(
            @NotBlank String customerId,
            @NotNull HolderRole role,
            @Size(max = 30) String authorizationType,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal ownershipPercentage
    ) {}

    public record NomineeRequest(
            String customerReference,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 30) String relationshipCode,
            @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal allocationPercentage
    ) {}

    public record LimitRequest(
            @NotNull LimitType limitType,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo
    ) {}

    public record MandateRequest(
            @NotBlank String authorizedCustomerId,
            @NotBlank @Size(max = 30) String mandateType,
            @NotNull OffsetDateTime validFrom,
            OffsetDateTime validTo
    ) {}

    public record StatusCommand(
            @NotBlank @Size(max = 40) String reasonCode,
            @Size(max = 500) String reasonText,
            OffsetDateTime effectiveAt
    ) {}
}
