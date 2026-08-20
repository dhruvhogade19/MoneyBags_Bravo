package com.moneybags.eod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class DemoEodAutoResume {
    private static final Logger LOG = LoggerFactory.getLogger(DemoEodAutoResume.class);
    private final EodOrchestrationService service;
    private final DemoStepPolicy demoStepPolicy;
    private final String runId;

    DemoEodAutoResume(EodOrchestrationService service,
                      DemoStepPolicy demoStepPolicy,
                      @Value("${moneybags.eod.demo.auto-resume-run-id:}") String runId) {
        this.service = service;
        this.demoStepPolicy = demoStepPolicy;
        this.runId = runId == null ? "" : runId.trim();
    }

    @EventListener(ApplicationReadyEvent.class)
    void resumeConfiguredDemoRun() {
        if (!demoStepPolicy.allStepsEnabled() || runId.isEmpty()) return;
        try {
            service.resume(runId);
        } catch (RuntimeException exception) {
            // Demo convenience must never make an otherwise healthy production service fail startup.
            LOG.error("Configured demo EOD run {} could not be resumed", runId, exception);
        }
    }
}
