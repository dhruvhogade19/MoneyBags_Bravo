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

@SpringBootTest
@ActiveProfiles("test")
class EodOrchestrationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 13);

    @Autowired EodOrchestrationService service;
    @Autowired StubPeerOperations peers;
    @Autowired EodRunRepository runs;
    @Autowired EodBusinessDateRepository businessDates;

    @BeforeEach
    void resetPeerStub() {
        exceptions.deleteAll();
        runs.deleteAll();
        businessDates.deleteAll();
        peers.reset();
    }

    @Autowired EodExceptionRepository exceptions;

    @Test
    void invokesEveryRealPeerContractAndReloadsTheCompletedRunFromTheDatabase() {
        var started = service.start("eod-2026-08-13", new StartEodRunRequest(DATE, "operations-user"));
        var response = awaitTerminal(started.runId());

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.steps()).hasSize(15).allMatch(step -> step.status().equals("COMPLETED"));
        assertThat(peers.invoked).containsExactlyElementsOf(EodOrchestrationService.STEPS.stream()
                .map(step -> step.method() + " " + step.path()).toList());
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE.plusDays(1));
        assertThat(service.businessDate().status()).isEqualTo("OPEN");

        var reloaded = service.get(response.runId());
        assertThat(reloaded.status()).isEqualTo("COMPLETED");
        assertThat(reloaded.steps()).hasSize(15);
        assertThat(runs.count()).isEqualTo(1);
        assertThat(businessDates.count()).isEqualTo(1);

        int callsBeforeReplay = peers.invoked.size();
        var replay = service.start("eod-2026-08-13", new StartEodRunRequest(DATE, "ignored"));
        assertThat(replay.runId()).isEqualTo(response.runId());
        assertThat(peers.invoked).hasSize(callsBeforeReplay);
    }

    @Test
    void persistsAFailureAndResumesTheSameRunAtTheFailedStep() {
        peers.failOnceAt("DEPOSIT_READINESS");

        var started = service.start("retry-key", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitTerminal(started.runId());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.errorCode()).isEqualTo("DEPOSIT_NOT_READY"));

        var storedFailure = service.get(failed.runId());
        assertThat(storedFailure.status()).isEqualTo("FAILED");
        assertThat(storedFailure.steps().get(4).status()).isEqualTo("FAILED");

        service.resume(failed.runId());
        var completed = awaitCompleted(failed.runId());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.steps()).allMatch(step -> step.status().equals("COMPLETED"));
        assertThat(completed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.status()).isEqualTo("RESOLVED"));
        assertThat(completed.steps().get(4).attemptCount()).isEqualTo(2);
    }

    private EodController.EodRunResponse awaitTerminal(String runId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            var response = service.get(runId);
            if ("COMPLETED".equals(response.status()) || "FAILED".equals(response.status())) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for EOD run", exception);
            }
        }
        throw new AssertionError("EOD run did not reach a terminal state");
    }

    private EodController.EodRunResponse awaitCompleted(String runId) {
        for (int attempt = 0; attempt < 200; attempt++) {
            var response = service.get(runId);
            if ("COMPLETED".equals(response.status())) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for resumed EOD run", exception);
            }
        }
        throw new AssertionError("Resumed EOD run did not complete");
    }

    @TestConfiguration
    static class StubConfiguration {
        @Bean
        @Primary
        StubPeerOperations stubPeerOperations() { return new StubPeerOperations(); }
    }

    static final class StubPeerOperations implements PeerOperations {
        private final List<String> invoked = new ArrayList<>();
        private String failureStep;
        private boolean failed;

        void reset() {
            invoked.clear();
            failureStep = null;
            failed = false;
        }

        void failOnceAt(String stepCode) { failureStep = stepCode; }

        @Override
        public Map<String, Object> execute(StepDefinition step, EodContext context,
                                           Map<String, Map<String, Object>> outputs) {
            invoked.add(step.method() + " " + step.path());
            if (!failed && step.code().equals(failureStep)) {
                failed = true;
                throw new PeerOperationException("DEPOSIT_NOT_READY", "Active reservations remain",
                        Map.of("ready", false));
            }
            return Map.of("source", step.providerService(), "step", step.code());
        }
    }
}
