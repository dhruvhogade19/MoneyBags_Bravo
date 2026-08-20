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
        "moneybags.eod.demo.skipped-steps=*"
})
@ActiveProfiles("test")
class EodNonDemoWildcardIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 13);

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
    void wildcardCannotBypassPeersWithoutTheDemoSpringProfile() {
        assertThat(demoStepPolicy.allStepsEnabled()).isFalse();

        var started = service.start("non-demo-wildcard-run", new StartEodRunRequest(DATE, "operator"));
        var completed = awaitCompleted(started.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.invoked).containsExactlyElementsOf(EodOrchestrationService.STEPS.stream()
                .map(StepDefinition::code).toList());
        assertThat(completed.steps()).allSatisfy(step ->
                assertThat(step.output()).doesNotContainKey("demoMode"));
    }

    private EodController.EodRunResponse awaitCompleted(String runId) {
        for (int attempt = 0; attempt < 300; attempt++) {
            var response = service.get(runId);
            if ("COMPLETED".equals(response.status())) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for non-demo EOD", exception);
            }
        }
        throw new AssertionError("Non-demo EOD did not complete");
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
            return Map.of("status", "COMPLETED", "stepCode", step.code());
        }
    }
}
