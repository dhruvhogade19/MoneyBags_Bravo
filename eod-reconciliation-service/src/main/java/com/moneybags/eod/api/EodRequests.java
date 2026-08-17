package com.moneybags.eod.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public final class EodRequests {
    private EodRequests() {}
    public record StartEodRunRequest(@NotNull LocalDate businessDate, @NotBlank String startedBy) {}
    public record EodResumeRequest(@NotBlank String requestedBy, @NotBlank String reason) {}
    public record EodStepRetryRequest(@NotBlank String requestedBy, @NotBlank String reason) {}
    public record EodExceptionResolutionRequest(@NotBlank String resolution, @NotBlank String resolvedBy, boolean waived) {}
    public record OpenBusinessDateRequest(@NotNull LocalDate businessDate, @NotBlank String openedBy) {}
}
