package com.moneybags.eod;

import com.moneybags.eod.EodController.StartEodRunRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "moneybags.eod.demo.enabled=true",
        "moneybags.eod.demo.skipped-steps=STATEMENTS_GENERATE,FIXED_DEPOSIT_RECONCILIATION,"
                + "ACCOUNTING_PERIOD_CLOSE,PAYMENTS_REOPEN"
})
@ActiveProfiles({"test", "demo"})
class EodDemoSkipIntegrationTest {
    @Autowired EodOrchestrationService service;
    @Autowired RecordingPeerOperations peers;
    @Autowired EodRunRepository runs;
    @Autowired EodBusinessDateRepository businessDates;
    @Autowired EodExceptionRepository exceptions;
    @Autowired DemoStepPolicy demoStepPolicy;

    @BeforeEach
    void reset() {
        exceptions.deleteAll();
        runs.deleteAll();
        businessDates.deleteAll();
        peers.invoked.clear();
    }

    @Test
    void demoConfigurationCannotBypassFinancialOrPaymentFenceControls() {
        var started = service.start("demo-skip-run",
                new StartEodRunRequest(LocalDate.of(2026, 8, 13), "demo-operator"));
        var completed = awaitCompleted(started.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.invoked).contains("FIXED_DEPOSIT_RECONCILIATION", "ACCOUNTING_PERIOD_CLOSE",
                "PAYMENTS_REOPEN");
        assertThat(completed.steps()).filteredOn(step -> List.of("FIXED_DEPOSIT_RECONCILIATION",
                        "ACCOUNTING_PERIOD_CLOSE", "PAYMENTS_REOPEN").contains(step.stepCode()))
                .allSatisfy(step -> assertThat(step.output()).doesNotContainEntry("status", "SKIPPED"));
        assertThat(demoStepPolicy.skips("STATEMENTS_GENERATE")).isTrue();
        assertThat(demoStepPolicy.skips("FIXED_DEPOSIT_RECONCILIATION")).isFalse();
        assertThat(demoStepPolicy.skips("ACCOUNTING_PERIOD_CLOSE")).isFalse();
        assertThat(demoStepPolicy.skips("PAYMENTS_REOPEN")).isFalse();
    }

    private EodController.EodRunResponse awaitCompleted(String runId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            var response = service.get(runId);
            if ("COMPLETED".equals(response.status())) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for EOD", exception);
            }
        }
        throw new AssertionError("Demo EOD run did not complete");
    }

    @TestConfiguration
    static class StubConfiguration {
        @Bean
        @Primary
        RecordingPeerOperations recordingPeerOperations() {
            return new RecordingPeerOperations();
        }
    }

    static final class RecordingPeerOperations implements PeerOperations {
        private final List<String> invoked = new ArrayList<>();

        @Override
        public Map<String, Object> execute(StepDefinition step, EodContext context,
                                           Map<String, Map<String, Object>> outputs) {
            invoked.add(step.code());
            return Map.of("status", "COMPLETED", "step", step.code());
        }
    }
}
