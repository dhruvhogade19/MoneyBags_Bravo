package com.moneybags.creditcard.dto;

import com.moneybags.creditcard.domain.CreditCardTypes.AccountStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.EligibilityStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.ApplicationStatus;
import com.moneybags.creditcard.domain.CreditCardTypes.HoldStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CreditCardDtos {
    private CreditCardDtos() {
    }

    @Schema(description = "Credit-card application submitted by a customer or channel.")
    public record ApplicationRequest(@Schema(description = "Customer CIF identifier.", example = "101") @NotNull @Positive Long cifId,
                                     @Schema(description = "Credit-card product code.", example = "CARD-GOLD") @NotBlank String productCode,
                                     @Schema(description = "Requested credit limit; must be positive.", example = "100000.00") @NotNull @DecimalMin("0.01") BigDecimal requestedCreditLimit) {
    }

    @Schema(description = "Request to create an account from an approved application.")
    public record AccountCreateRequest(@Schema(description = "Approved application identifier.", example = "1001") @NotNull @Positive Long applicationId) {
    }

    @Schema(description = "Payment Service request to reserve card credit.")
    public record HoldRequest(@Schema(description = "Unique Payment Service reference used for idempotency.", example = "PAY-12345") @NotBlank String referenceId,
                              @Schema(description = "Credit amount to reserve; must be positive.", example = "50000.00") @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    @Schema(description = "Bill-payment amount received from Payment Service.")
    public record AmountRequest(@Schema(description = "Amount received; must be positive. Excess over current outstanding is ignored.", example = "25000.00") @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    @Schema(description = "Persisted credit-card application and eligibility decision.")
    public record ApplicationResponse(Long applicationId, Long cifId, String productCode,
                                      BigDecimal requestedCreditLimit, BigDecimal approvedCreditLimit,
                                      BigDecimal purchaseInterestRateSnapshot, ApplicationStatus applicationStatus,
                                      String kycStatusSnapshot, Integer age, BigDecimal salary,
                                      EligibilityStatus eligibilityStatus, OffsetDateTime submittedAt,
                                      OffsetDateTime updatedAt) {
    }

    @Schema(description = "Credit-card account details and current credit state.")
    public record AccountResponse(Long accountId, Long applicationId, Long cifId, String productCode, Integer age,
                                  BigDecimal salary, String cardNumber, BigDecimal sanctionedLimit,
                                  BigDecimal purchaseInterestRateSnapshot, BigDecimal availableLimit,
                                  BigDecimal outstandingAmount, AccountStatus status, OffsetDateTime openedAt) {
    }

    @Schema(description = "Credit reservation hold returned to Payment Service.")
    public record HoldResponse(Long holdId, Long accountId, String referenceId, BigDecimal amount, HoldStatus status,
                               OffsetDateTime createdAt) {
    }

    @Schema(description = "Read-only available credit limit.")
    public record LimitResponse(Long accountId, BigDecimal availableLimit) {
    }

    @Schema(description = "Account purchase interest rate snapshot.")
    public record InterestRateResponse(Long accountId, BigDecimal purchaseInterestRate) {
    }

    @Schema(description = "End-of-day readiness and any closure blockers.")
    public record EodReadinessResponse(boolean readyForEod, long activeAccountCount, long blockedAccountCount,
                                       long pendingApplicationCount, List<String> closureBlockers) {
    }
}
