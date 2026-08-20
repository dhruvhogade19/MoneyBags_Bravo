package com.moneybags.eod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Production recovery loop for JVM/process loss. Step leases remain the source of truth, so every
 * application instance may scan safely without executing the same step concurrently.
 */
@Component
class EodRecoveryCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(EodRecoveryCoordinator.class);
    private final EodOrchestrationService service;

    EodRecoveryCoordinator(EodOrchestrationService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    void recoverAtStartup() {
        recover("startup");
    }

    @Scheduled(initialDelayString = "${moneybags.eod.recovery.initial-delay-ms:60000}",
            fixedDelayString = "${moneybags.eod.recovery.fixed-delay-ms:30000}")
    void recoverPeriodically() {
        recover("scheduled");
    }

    private void recover(String trigger) {
        try {
            service.recoverInterruptedRuns();
        } catch (RuntimeException exception) {
            // A database/service outage must not terminate the scheduler. The next pass retries.
            LOG.error("EOD {} recovery scan failed", trigger, exception);
        }
    }
}
