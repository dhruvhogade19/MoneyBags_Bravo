package com.moneybags.deposit.closure.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class AccountClosureRequests {
    private AccountClosureRequests() {}
    public record ClosureQuoteRequest(@NotBlank String customerId, String destinationAccountId,
        @NotBlank @Size(max=30) String channel, @NotNull LocalDate requestedClosureDate) {}
    public record CasaClosureRequest(@NotBlank String customerId, String destinationAccountId,
        @NotBlank @Size(max=30) String channel, @NotBlank @Size(max=40) String reasonCode,
        @Size(max=500) String reasonText, @NotNull LocalDate requestedClosureDate) {}
    public record PrematureClosureQuoteRequest(@NotBlank String customerId,@NotBlank String destinationAccountId,
        @NotBlank @Size(max=30) String channel,@NotNull LocalDate requestedClosureDate) {}
    public record PrematureClosureRequest(@NotBlank String customerId,@NotBlank String destinationAccountId,
        @NotBlank @Size(max=30) String channel,@NotBlank @Size(max=40) String reasonCode,
        @Size(max=500) String reasonText,@NotNull LocalDate requestedClosureDate) {}
    public record CancelClosureRequest(@NotBlank @Size(max=40) String reasonCode) {}
}
