package com.moneybags.eod.api;

import com.moneybags.eod.api.EodRequests.EodExceptionResolutionRequest;
import com.moneybags.eod.api.EodRequests.EodResumeRequest;
import com.moneybags.eod.api.EodRequests.EodStepRetryRequest;
import com.moneybags.eod.api.EodRequests.OpenBusinessDateRequest;
import com.moneybags.eod.api.EodRequests.StartEodRunRequest;
import com.moneybags.eod.api.EodResponses.BusinessDateResponse;
import com.moneybags.eod.api.EodResponses.EodRunResponse;
import com.moneybags.eod.service.EodOrchestrationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class EodController {
    private final EodOrchestrationService service;
    public EodController(EodOrchestrationService service) { this.service = service; }

    @GetMapping("/business-date")
    BusinessDateResponse businessDate() { return service.currentBusinessDate(); }

    @PostMapping("/eod/runs")
    EodRunResponse start(@RequestHeader("Idempotency-Key") @NotBlank String key,
                         @Valid @RequestBody StartEodRunRequest request) { return service.start(key, request); }

    @GetMapping("/eod/runs/{runId}")
    EodRunResponse get(@PathVariable String runId) { return service.get(runId); }

    @PostMapping("/eod/runs/{runId}/resume")
    EodRunResponse resume(@PathVariable String runId, @Valid @RequestBody EodResumeRequest request) {
        return service.resume(runId, request);
    }

    @PostMapping("/eod/runs/{runId}/steps/{stepCode}/retry")
    EodRunResponse retry(@PathVariable String runId, @PathVariable String stepCode,
                         @Valid @RequestBody EodStepRetryRequest request) { return service.retry(runId, stepCode, request); }

    @PostMapping("/eod/exceptions/{exceptionId}/resolve")
    EodRunResponse resolve(@PathVariable String exceptionId,
                           @Valid @RequestBody EodExceptionResolutionRequest request) { return service.resolve(exceptionId, request); }

    @PostMapping("/business-date/open-next")
    BusinessDateResponse openNext(@Valid @RequestBody OpenBusinessDateRequest request) { return service.openNext(request); }
}
