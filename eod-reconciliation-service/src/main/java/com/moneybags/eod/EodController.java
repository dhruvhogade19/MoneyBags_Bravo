package com.moneybags.eod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
public class EodController {
    private final EodOrchestrationService service;

    public EodController(EodOrchestrationService service) { this.service = service; }

    @GetMapping("/business-date")
    BusinessDateResponse businessDate() { return service.businessDate(); }

    @PostMapping("/eod/runs")
    @ResponseStatus(HttpStatus.CREATED)
    EodRunResponse start(@RequestHeader("Idempotency-Key") @NotBlank String key,
                         @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                         @RequestBody @Valid StartEodRunRequest request) {
        return service.start(key, request, authorization);
    }

    @GetMapping("/eod/runs/{runId}")
    EodRunResponse get(@PathVariable String runId) { return service.get(runId); }

    @GetMapping("/eod/runs")
    List<EodRunResponse> list(@RequestParam(required = false) LocalDate businessDate) {
        return service.list(businessDate);
    }

    @PostMapping("/eod/runs/{runId}/resume")
    EodRunResponse resume(@PathVariable String runId,
                          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                          @RequestHeader(value = "Idempotency-Key", required = false)
                          @Size(min = 1, max = 200) String idempotencyKey,
                          @RequestBody @Valid EodResumeRequest request) {
        return service.resume(runId, request, authorization, idempotencyKey);
    }

    @PostMapping("/eod/runs/{runId}/steps/{stepCode}/retry")
    EodRunResponse retry(@PathVariable String runId, @PathVariable String stepCode,
                         @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                         @RequestHeader(value = "Idempotency-Key", required = false)
                         @Size(min = 1, max = 200) String idempotencyKey,
                         @RequestBody @Valid EodStepRetryRequest request) {
        return service.retry(runId, stepCode, request, authorization, idempotencyKey);
    }

    @PostMapping("/eod/exceptions/{exceptionId}/resolve")
    EodRunResponse resolve(@PathVariable String exceptionId,
                           @RequestHeader(value = "Idempotency-Key", required = false)
                           @Size(min = 1, max = 200) String idempotencyKey,
                           @RequestBody @Valid EodExceptionResolutionRequest request) {
        return service.resolve(exceptionId, request, idempotencyKey);
    }

    @PostMapping("/business-date/open-next")
    BusinessDateResponse openNext(@RequestBody @Valid OpenBusinessDateRequest request) {
        return service.openNext(request);
    }

    public record StartEodRunRequest(LocalDate businessDate, @NotBlank String startedBy) {}
    public record EodResumeRequest(@NotBlank String requestedBy, String reason) {}
    public record EodStepRetryRequest(@NotBlank String requestedBy, String reason) {}
    public record EodExceptionResolutionRequest(@NotBlank String resolution, @NotBlank String resolvedBy,
                                                boolean waived) {}
    public record OpenBusinessDateRequest(LocalDate businessDate, @NotBlank String openedBy) {}
    public record BusinessDateResponse(LocalDate businessDate, String status, Instant cutoffAt, Instant openedAt,
                                       Instant closedAt, long version) {}
    public record EodRunResponse(String runId, LocalDate businessDate, String status, String startedBy,
                                 Instant startedAt, Instant completedAt, List<StepResponse> steps,
                                 List<ExceptionResponse> exceptions, long version) {}
    public record StepResponse(String stepCode, int sequence, String providerService, String method, String path,
                               String status, String commandReference, int attemptCount, Instant startedAt,
                               Instant completedAt, String errorCode, String message, Map<String, Object> output) {}
    public record ExceptionResponse(String exceptionId, String stepCode, String severity, String errorCode,
                                    Map<String, Object> details, String status, String resolution,
                                    String resolvedBy, Instant resolvedAt) {}
}
