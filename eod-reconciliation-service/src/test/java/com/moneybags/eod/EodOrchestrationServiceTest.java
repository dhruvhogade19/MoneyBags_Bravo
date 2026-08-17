package com.moneybags.eod;

import com.moneybags.eod.EodController.StartEodRunRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EodOrchestrationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 13);

    @Test
    void invokesEveryRealPeerContractInOrderAndAdvancesTheBusinessDate() {
        List<String> invoked = new ArrayList<>();
        PeerOperations peers = (step, context, outputs) -> {
            invoked.add(step.method() + " " + step.path());
            return Map.of("source", step.providerService(), "step", step.code());
        };
        EodOrchestrationService service = new EodOrchestrationService(peers, DATE, "INR");

        var response = service.start("eod-2026-08-13", new StartEodRunRequest(DATE, "operations-user"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.steps()).hasSize(16).allMatch(step -> step.status().equals("COMPLETED"));
        assertThat(invoked).containsExactlyElementsOf(EodOrchestrationService.STEPS.stream()
                .map(step -> step.method() + " " + step.path()).toList());
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE.plusDays(1));
        assertThat(service.businessDate().status()).isEqualTo("OPEN");
    }

    @Test
    void stopsAtARealUpstreamFailureAndResumesWithTheSameRun() {
        List<String> invoked = new ArrayList<>();
        boolean[] failOnce = {true};
        PeerOperations peers = (step, context, outputs) -> {
            invoked.add(step.code());
            if (step.code().equals("DEPOSIT_READINESS") && failOnce[0]) {
                failOnce[0] = false;
                throw new PeerOperationException("DEPOSIT_NOT_READY", "Active reservations remain",
                        Map.of("ready", false));
            }
            return Map.of("step", step.code());
        };
        EodOrchestrationService service = new EodOrchestrationService(peers, DATE, "INR");

        var failed = service.start("retry-key", new StartEodRunRequest(DATE, "operations-user"));
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.errorCode()).isEqualTo("DEPOSIT_NOT_READY"));

        var completed = service.resume(failed.runId());
        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.steps()).allMatch(step -> step.status().equals("COMPLETED"));
        assertThat(completed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.status()).isEqualTo("RESOLVED"));
        assertThat(service.start("retry-key", new StartEodRunRequest(DATE, "ignored")).runId())
                .isEqualTo(failed.runId());
    }
}
