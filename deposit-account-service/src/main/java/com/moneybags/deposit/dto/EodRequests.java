package com.moneybags.deposit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public final class EodRequests {
    private EodRequests() {}

    public record DepositAccrualRequest(
            @NotBlank String eodRunId,
            @NotBlank String commandReference,
            LocalDate businessDate,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {}
}
