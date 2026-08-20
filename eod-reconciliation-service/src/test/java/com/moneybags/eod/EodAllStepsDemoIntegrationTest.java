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
@ActiveProfiles({"test", "demo"})
class EodAllStepsDemoIntegrationTest {
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
    void explicitWildcardCompletesAllFifteenStepsLocallyAndOpensTheNextDate() {
        assertThat(demoStepPolicy.allStepsEnabled()).isTrue();

        var started = service.start("all-steps-demo-run", new StartEodRunRequest(DATE, "demo-operator"));
        var completed = awaitCompleted(started.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.steps()).hasSize(15).allSatisfy(step -> {
            assertThat(step.status()).isEqualTo("COMPLETED");
            assertThat(step.output()).containsEntry("demoMode", true)
                    .containsEntry("bypassed", true)
                    .containsEntry("controlBypassed", true)
                    .containsEntry("syntheticSuccess", true)
                    .doesNotContainEntry("status", "SKIPPED");
        });
        assertThat(outputFor(completed, "PAYMENTS_CUTOFF"))
                .containsEntry("status", "CUT_OFF")
                .containsEntry("pendingPayments", 0);
        assertThat(outputFor(completed, "CREDIT_CARD_READINESS"))
                .containsEntry("ready", true)
                .containsEntry("readyForEod", true)
                .containsEntry("activeAccountCount", 0)
                .containsEntry("blockedAccountCount", 0)
                .containsEntry("pendingApplicationCount", 0)
                .containsEntry("closureBlockers", List.of());
        assertThat(outputFor(completed, "DEPOSIT_READINESS"))
                .containsEntry("depositAccounts", Map.of(
                        "service", "deposit-account-service",
                        "businessDate", DATE.toString(),
                        "ready", true,
                        "blockers", List.of()))
                .containsEntry("fixedDeposits", Map.of(
                        "ready", true,
                        "pendingFunding", 0,
                        "pendingPayouts", 0,
                        "blockers", List.of()));
        assertThat(outputFor(completed, "DEPOSIT_ACCRUALS"))
                .containsEntry("processedCount", 0)
                .containsEntry("skipped", 0);
        assertThat(outputFor(completed, "FIXED_DEPOSIT_ACCRUALS"))
                .containsEntry("processed", 0)
                .containsEntry("skipped", 0);
        assertThat(outputFor(completed, "FIXED_DEPOSIT_MATURITIES"))
                .containsEntry("processed", 0)
                .containsEntry("skipped", 0);
        assertThat(outputFor(completed, "BILLS_CLOSE"))
                .containsEntry("billsProcessed", 0)
                .containsEntry("pendingBillReferences", List.of());
        assertThat(outputFor(completed, "TRIAL_BALANCE"))
                .containsEntry("status", "BALANCED")
                .containsEntry("runId", completed.runId())
                .containsEntry("totalDebit", 0)
                .containsEntry("totalCredit", 0)
                .containsEntry("lines", List.of());
        assertThat(outputFor(completed, "PAYMENTS_RECONCILIATION"))
                .containsEntry("runId", completed.runId())
                .containsEntry("expectedTotalDebit", 0)
                .containsEntry("actualTotalDebit", 0)
                .containsEntry("items", List.of());
        assertThat(outputFor(completed, "FIXED_DEPOSIT_RECONCILIATION"))
                .containsEntry("runId", completed.runId())
                .containsEntry("expectedTotalDebit", 0)
                .containsEntry("actualTotalDebit", 0)
                .containsEntry("items", List.of());
        assertThat(completed.steps()).filteredOn(step -> "PAYMENTS_REOPEN".equals(step.stepCode()))
                .singleElement().satisfies(step -> assertThat(step.output())
                        .containsEntry("status", "OPEN")
                        .containsEntry("newPaymentIntake", true)
                        .containsEntry("pendingPayments", 0)
                        .containsEntry("postedJournalCount", 0)
                        .containsEntry("postedDebitTotal", 0)
                        .containsEntry("businessDate", DATE.plusDays(1).toString()));
        assertThat(peers.invoked).isEmpty();
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE.plusDays(1));
        assertThat(service.businessDate().status()).isEqualTo("OPEN");
    }

    private Map<String, Object> outputFor(EodController.EodRunResponse response, String stepCode) {
        return response.steps().stream()
                .filter(step -> stepCode.equals(step.stepCode()))
                .findFirst()
                .orElseThrow()
                .output();
    }

    private EodController.EodRunResponse awaitCompleted(String runId) {
        for (int attempt = 0; attempt < 300; attempt++) {
            var response = service.get(runId);
            if ("COMPLETED".equals(response.status())) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for all-step demo EOD", exception);
            }
        }
        throw new AssertionError("All-step demo EOD did not complete");
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
            return Map.of("status", "COMPLETED");
        }
    }
}
