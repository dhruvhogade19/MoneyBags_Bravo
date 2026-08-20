package com.moneybags.eod;

import com.moneybags.eod.EodController.StartEodRunRequest;
import com.moneybags.eod.EodController.EodExceptionResolutionRequest;
import com.moneybags.eod.EodController.EodResumeRequest;
import com.moneybags.eod.EodController.EodStepRetryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class EodOrchestrationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 13);

    @Autowired EodOrchestrationService service;
    @Autowired StubPeerOperations peers;
    @Autowired EodRunRepository runs;
    @Autowired EodBusinessDateRepository businessDates;
    @Autowired EodRunActionRepository actions;
    @Autowired PlatformTransactionManager transactionManager;

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
        assertThat(response.steps().get(4).path())
                .isEqualTo("/api/deposit-accounts/operations/eod/readiness");
        assertThat(response.steps().get(6).path())
                .isEqualTo("/api/deposit-accounts/operations/eod/fixed-deposit-accruals");
        assertThat(peers.invoked).containsExactlyElementsOf(EodOrchestrationService.STEPS.stream()
                .map(step -> step.method() + " " + step.path()).toList());
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE.plusDays(1));
        assertThat(service.businessDate().status()).isEqualTo("OPEN");

        var reloaded = service.get(response.runId());
        assertThat(reloaded.status()).isEqualTo("COMPLETED");
        assertThat(reloaded.steps()).hasSize(15);
        assertThat(runs.count()).isEqualTo(1);
        assertThat(businessDates.count()).isEqualTo(1);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(response.runId()))
                .extracting(EodRunActionEntity::actionType)
                .containsSubsequence("ROLLOVER_READY", "COMPLETE");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EodRunEntity persisted = runs.findById(response.runId()).orElseThrow();
            assertThat(persisted.workflowVersion()).isEqualTo(EodWorkflowRegistry.CURRENT_VERSION);
            assertThat(persisted.requireStep("TRIAL_BALANCE").dependencies())
                    .containsExactly("DEPOSIT_ACCRUALS", "FIXED_DEPOSIT_MATURITIES", "BILLS_CLOSE");
            assertThat(persisted.requireStep("PAYMENTS_REOPEN").executionMode())
                    .isEqualTo(StepExecutionMode.ALWAYS_RUN);
        });

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

    @Test
    void retriesTransientUpstreamFailuresWithoutChangingTheExecutionEpoch() {
        peers.failAt("FIXED_DEPOSIT_ACCRUALS", "UPSTREAM_HTTP_500", 2);

        var started = service.start("transient-retry-key", new StartEodRunRequest(DATE, "operations-user"));
        var completed = awaitCompleted(started.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.steps()).filteredOn(step -> step.stepCode().equals("FIXED_DEPOSIT_ACCRUALS"))
                .singleElement().satisfies(step -> assertThat(step.attemptCount()).isEqualTo(3));
        assertThat(peers.epochsFor("FIXED_DEPOSIT_ACCRUALS")).containsExactly(1, 1, 1);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(started.runId()))
                .filteredOn(action -> action.actionType().equals("AUTO_RETRY"))
                .hasSize(2).allSatisfy(action -> assertThat(action.stepCode())
                        .isEqualTo("FIXED_DEPOSIT_ACCRUALS"));
    }

    @Test
    void pollsPaymentsDrainBehindTheSameFenceUntilItReportsDrained() {
        peers.failAt("PAYMENTS_DRAIN", "PAYMENTS_NOT_DRAINED", 2);

        var started = service.start("drain-poll-key", new StartEodRunRequest(DATE, "operations-user"));
        var completed = awaitCompleted(started.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.epochsFor("PAYMENTS_DRAIN")).containsExactly(1, 1, 1);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(started.runId()))
                .filteredOn(action -> "AUTO_RETRY".equals(action.actionType())
                        && "PAYMENTS_DRAIN".equals(action.stepCode()))
                .hasSize(2);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(runs.findById(started.runId()).orElseThrow()
                        .requireStep("PAYMENTS_DRAIN").definition().maxAttempts()).isEqualTo(9));
    }

    @Test
    void reopensAfterAPreMutationFailureAndReestablishesTheBarrierBeforeResume() {
        peers.failAt("DEPOSIT_READINESS", "DEPOSIT_NOT_READY", 1);

        var started = service.start("finalizer-resume-key", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitFailedWithFinalizer(started.runId());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(peers.codes()).endsWith("DEPOSIT_READINESS", "PAYMENTS_REOPEN");
        assertThat(peers.epochsFor("PAYMENTS_REOPEN")).containsExactly(1);

        service.resume(failed.runId(), new EodResumeRequest("bank-admin", "Deposit posting repaired"), null);
        var completed = awaitCompleted(failed.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.epochsFor("PAYMENTS_CUTOFF")).containsExactly(1, 2);
        assertThat(peers.epochsFor("PAYMENTS_DRAIN")).containsExactly(1, 2);
        assertThat(peers.epochsFor("DEPOSIT_READINESS")).containsExactly(1, 2);
        assertThat(peers.epochsFor("PAYMENTS_REOPEN")).containsExactly(1, 2);

        List<String> secondPass = peers.codes().subList(peers.codes().indexOf("PAYMENTS_REOPEN") + 1,
                peers.codes().size());
        assertThat(secondPass).startsWith("ACCOUNTING_PERIOD_OPEN_CURRENT", "PAYMENTS_CUTOFF", "PAYMENTS_DRAIN")
                .containsSubsequence("PAYMENTS_DRAIN", "DEPOSIT_READINESS")
                .endsWith("PAYMENTS_REOPEN");
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(failed.runId()))
                .filteredOn(action -> action.actionType().equals("RESUME"))
                .singleElement().satisfies(action -> {
                    assertThat(action.requestedBy()).isEqualTo("bank-admin");
                    assertThat(action.reason()).isEqualTo("Deposit posting repaired");
                });
    }

    @Test
    void manualRetryUsesAFreshEpochForAPreviouslyCachedStructuredFailure() {
        peers.failAt("BILLS_CLOSE", "BILL_CLOSE_FAILED", 1);

        var started = service.start("manual-epoch-key", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitHeldForRecovery(started.runId());

        service.retry(failed.runId(), "BILLS_CLOSE",
                new EodStepRetryRequest("bank-admin", "Billing corrected its cached result"), null);
        var completed = awaitCompleted(failed.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.epochsFor("BILLS_CLOSE")).containsExactly(1, 2);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(failed.runId()))
                .filteredOn(action -> action.actionType().equals("RETRY"))
                .singleElement().satisfies(action -> {
                    assertThat(action.requestedBy()).isEqualTo("bank-admin");
                    assertThat(action.reason()).isEqualTo("Billing corrected its cached result");
                    assertThat(action.stepCode()).isEqualTo("BILLS_CLOSE");
                });
    }

    @Test
    void duplicateResumeRequestKeyReplaysWithoutRearmingTheWorkflowTwice() {
        peers.failAt("DEPOSIT_READINESS", "DEPOSIT_NOT_READY", 1);

        var started = service.start("resume-action-start", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitFailedWithFinalizer(started.runId());
        var request = new EodResumeRequest("bank-admin", "Deposit blockers cleared");

        service.resume(failed.runId(), request, null, "resume-action-key");
        service.resume(failed.runId(), request, null, "resume-action-key");
        assertThatThrownBy(() -> service.resume(failed.runId(),
                new EodResumeRequest("bank-admin", "Different repair"), null, "resume-action-key"))
                .isInstanceOf(EodConflictException.class)
                .hasMessageContaining("different RESUME request");

        awaitCompleted(failed.runId());
        assertThat(peers.epochsFor("PAYMENTS_CUTOFF")).containsExactly(1, 2);
        assertThat(peers.epochsFor("DEPOSIT_READINESS")).containsExactly(1, 2);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(failed.runId()))
                .filteredOn(action -> "RESUME".equals(action.requestKind())
                        && "resume-action-key".equals(action.requestKey()))
                .singleElement().satisfies(action -> {
                    assertThat(action.actionType()).isEqualTo("RESUME");
                    assertThat(action.requestHash()).hasSize(64);
                });
    }

    @Test
    void duplicateRetryRequestKeyReplaysAfterTheFailedStepHasBeenRearmed() {
        peers.failAt("BILLS_CLOSE", "BILL_CLOSE_FAILED", 1);

        var started = service.start("retry-action-start", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitHeldForRecovery(started.runId());
        var request = new EodStepRetryRequest("bank-admin", "Billing repair completed");

        service.retry(failed.runId(), "BILLS_CLOSE", request, null, "retry-action-key");
        service.retry(failed.runId(), "BILLS_CLOSE", request, null, "retry-action-key");
        assertThatThrownBy(() -> service.retry(failed.runId(), "BILLS_CLOSE",
                new EodStepRetryRequest("bank-admin", "Different repair"), null, "retry-action-key"))
                .isInstanceOf(EodConflictException.class)
                .hasMessageContaining("different STEP_RETRY request");

        awaitCompleted(failed.runId());
        assertThat(peers.epochsFor("BILLS_CLOSE")).containsExactly(1, 2);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(failed.runId()))
                .filteredOn(action -> "STEP_RETRY".equals(action.requestKind())
                        && "retry-action-key".equals(action.requestKey()))
                .singleElement().satisfies(action -> assertThat(action.actionType()).isEqualTo("RETRY"));
    }

    @Test
    void duplicateExceptionResolutionRequestKeyReplaysButChangedReuseConflicts() {
        peers.failAt("DEPOSIT_READINESS", "DEPOSIT_NOT_READY", 1);

        var started = service.start("resolve-action-start", new StartEodRunRequest(DATE, "operations-user"));
        var failed = awaitFailedWithFinalizer(started.runId());
        String exceptionId = failed.exceptions().getFirst().exceptionId();
        var request = new EodExceptionResolutionRequest("Reservation released", "bank-admin", false);

        service.resolve(exceptionId, request, "resolve-action-key");
        service.resolve(exceptionId, request, "resolve-action-key");
        assertThatThrownBy(() -> service.resolve(exceptionId,
                new EodExceptionResolutionRequest("Different resolution", "bank-admin", false),
                "resolve-action-key"))
                .isInstanceOf(EodConflictException.class)
                .hasMessageContaining("different EXCEPTION_RESOLUTION request");

        var resolved = service.get(failed.runId());
        assertThat(resolved.exceptions()).singleElement().satisfies(exception -> {
            assertThat(exception.status()).isEqualTo("RESOLVED");
            assertThat(exception.resolution()).isEqualTo("Reservation released");
        });
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(failed.runId()))
                .filteredOn(action -> "EXCEPTION_RESOLUTION".equals(action.requestKind())
                        && "resolve-action-key".equals(action.requestKey()))
                .singleElement().satisfies(action ->
                        assertThat(action.actionType()).isEqualTo("RESOLVE_EXCEPTION"));
    }

    @Test
    void holdsPaymentsAfterFinancialWorkAndResumesBehindTheSameFence() {
        peers.failAt("ACCOUNTING_PERIOD_CLOSE", "PERIOD_NOT_CLOSED", 1);

        var started = service.start("late-control-key", new StartEodRunRequest(DATE, "operations-user"));
        var held = awaitHeldForRecovery(started.runId());

        assertThat(held.status()).isEqualTo("FAILED");
        assertThat(peers.codes()).doesNotContain("PAYMENTS_REOPEN");
        assertThat(held.steps()).filteredOn(step -> step.stepCode().equals("PAYMENTS_REOPEN"))
                .singleElement().satisfies(step -> {
                    assertThat(step.status()).isEqualTo("FAILED");
                    assertThat(step.errorCode()).isEqualTo("HELD_FOR_EOD_RECOVERY");
                });

        service.resume(held.runId(), new EodResumeRequest("bank-admin", "Period close repaired"), null);
        var completed = awaitCompleted(held.runId());

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(peers.epochsFor("PAYMENTS_CUTOFF")).containsExactly(1, 1);
        assertThat(peers.epochsFor("PAYMENTS_DRAIN")).containsExactly(1, 1);
        assertThat(peers.epochsFor("TRIAL_BALANCE")).containsExactly(1);
        assertThat(peers.epochsFor("PAYMENTS_RECONCILIATION")).containsExactly(1);
        assertThat(peers.epochsFor("FIXED_DEPOSIT_RECONCILIATION")).containsExactly(1);
        assertThat(peers.epochsFor("ACCOUNTING_PERIOD_CLOSE")).containsExactly(1, 2);
        assertThat(peers.codes()).endsWith("PAYMENTS_CUTOFF", "PAYMENTS_DRAIN",
                "ACCOUNTING_PERIOD_CLOSE", "ACCOUNTING_PERIOD_OPEN_NEXT", "PAYMENTS_REOPEN");
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(held.runId()))
                .extracting(EodRunActionEntity::actionType)
                .contains("PAYMENTS_HELD_FOR_RECOVERY", "REASSERT_HELD_PAYMENT_BARRIER");
    }

    @Test
    void runsPersistedDependenciesEvenWhenTheirSequenceWouldSuggestTheOpposite() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("DEPOSIT_READINESS", 1, "deposit-account-service", "GET", "/legacy/ready",
                        List.of("ACCOUNTING_PERIOD_OPEN_CURRENT"), StepExecutionMode.REQUIRED, StepAuthMode.AUTO,
                        1, 0, "LEGACY-DEPENDENCY-TEST", ""),
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 2, "accounting-service", "POST",
                        "/legacy/open"),
                new StepDefinition("PAYMENTS_REOPEN", 3, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = runs.saveAndFlush(new EodRunEntity("dependency-run", "dependency-key", DATE,
                "operations-user", "LEGACY-DEPENDENCY-TEST", definitions));

        service.resume(run.id());
        awaitCompleted(run.id());

        assertThat(peers.codes()).containsExactly(
                "ACCOUNTING_PERIOD_OPEN_CURRENT", "DEPOSIT_READINESS", "PAYMENTS_REOPEN");
    }

    @Test
    void doesNotStealAnActiveDatabaseLeaseButRecoversAnExpiredOne() throws Exception {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                        "/legacy/open"),
                new StepDefinition("PAYMENTS_REOPEN", 2, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity active = new EodRunEntity("active-lease-run", "active-lease-key", DATE,
                "operations-user", definitions);
        active.markRunning();
        active.requireStep("ACCOUNTING_PERIOD_OPEN_CURRENT")
                .markRunning("other-worker", java.time.OffsetDateTime.now().plusMinutes(1));
        runs.saveAndFlush(active);

        service.resume(active.id());
        Thread.sleep(100);
        assertThat(peers.codes()).isEmpty();
        assertThat(service.get(active.id()).steps().getFirst().attemptCount()).isEqualTo(1);

        exceptions.deleteAll();
        runs.deleteAll();
        peers.reset();

        EodRunEntity expired = new EodRunEntity("expired-lease-run", "expired-lease-key", DATE,
                "operations-user", definitions);
        expired.markRunning();
        expired.requireStep("ACCOUNTING_PERIOD_OPEN_CURRENT")
                .markRunning("dead-worker", java.time.OffsetDateTime.now().minusSeconds(1));
        runs.saveAndFlush(expired);

        service.resume(expired.id());
        var completed = awaitCompleted(expired.id());
        assertThat(completed.steps().getFirst().attemptCount()).isEqualTo(2);
        assertThat(peers.codes()).containsExactly("ACCOUNTING_PERIOD_OPEN_CURRENT", "PAYMENTS_REOPEN");
    }

    @Test
    void recoveryReclaimsOnlyTheExpiredFinalizerAndNeverRetriesAFailedMainControl() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("DEPOSIT_READINESS", 5, "deposit-account-service", "GET", "/legacy/ready"),
                new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = new EodRunEntity("crashed-finalizer-run", "crashed-finalizer-key", DATE,
                "operations-user", definitions);
        EodRunStepEntity failed = run.requireStep("DEPOSIT_READINESS");
        failed.markRunning();
        run.markFailed(failed, "DEPOSIT_NOT_READY", "Reservations remain", "{}");
        run.requireStep("PAYMENTS_REOPEN")
                .markRunning("dead-finalizer-worker", java.time.OffsetDateTime.now().minusSeconds(1));
        runs.saveAndFlush(run);

        service.recoverInterruptedRuns();
        var recovered = awaitFailedWithFinalizer(run.id());

        assertThat(recovered.status()).isEqualTo("FAILED");
        assertThat(recovered.steps().getFirst().status()).isEqualTo("FAILED");
        assertThat(recovered.steps().getFirst().attemptCount()).isEqualTo(1);
        assertThat(peers.codes()).containsExactly("PAYMENTS_REOPEN");
    }

    @Test
    void recoveryRetriesOnlyTransientOrUnknownFailedPaymentFinalizers() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity transientRun = new EodRunEntity("transient-finalizer-run", "transient-finalizer-key", DATE,
                "operations-user", definitions);
        EodRunStepEntity transientFinalizer = transientRun.requireStep("PAYMENTS_REOPEN");
        transientFinalizer.markRunning();
        transientRun.markFailed(transientFinalizer, "UPSTREAM_HTTP_503", "Temporary outage", "{}",
                FailureClass.TRANSIENT);
        runs.saveAndFlush(transientRun);

        service.recoverInterruptedRuns();
        awaitCompleted(transientRun.id());

        assertThat(peers.epochsFor("PAYMENTS_REOPEN")).containsExactly(2);
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(transientRun.id()))
                .extracting(EodRunActionEntity::actionType).contains("RECOVER_FINALIZER");

        exceptions.deleteAll();
        runs.deleteAll();
        businessDates.deleteAll();
        peers.reset();

        EodRunEntity permanentRun = new EodRunEntity("permanent-finalizer-run", "permanent-finalizer-key", DATE,
                "operations-user", definitions);
        EodRunStepEntity permanentFinalizer = permanentRun.requireStep("PAYMENTS_REOPEN");
        permanentFinalizer.markRunning();
        permanentRun.markFailed(permanentFinalizer, "PAYMENT_FENCE_OWNER_MISMATCH", "Wrong fence owner", "{}",
                FailureClass.PERMANENT);
        runs.saveAndFlush(permanentRun);

        service.recoverInterruptedRuns();

        var notRetried = service.get(permanentRun.id());
        assertThat(notRetried.status()).isEqualTo("FAILED");
        assertThat(notRetried.steps().getFirst().attemptCount()).isEqualTo(1);
        assertThat(peers.codes()).isEmpty();
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(permanentRun.id()))
                .extracting(EodRunActionEntity::actionType).doesNotContain("RECOVER_FINALIZER");
    }

    @Test
    void legacyFinancialFailureRequiresManualFencingBeforeItCanClaimPaymentsAreHeld() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                        "/legacy/accounting/open"),
                new StepDefinition("PAYMENTS_CUTOFF", 2, "payments-service", "POST", "/legacy/cutoff"),
                new StepDefinition("PAYMENTS_DRAIN", 3, "payments-service", "POST", "/legacy/drain"),
                new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST",
                        "/legacy/fd-accruals"),
                new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = new EodRunEntity("legacy-open-payments-run", "legacy-open-payments-key", DATE,
                "operations-user", definitions);
        for (String code : List.of("ACCOUNTING_PERIOD_OPEN_CURRENT", "PAYMENTS_CUTOFF", "PAYMENTS_DRAIN")) {
            EodRunStepEntity step = run.requireStep(code);
            step.markRunning();
            step.markCompleted("{\"status\":\"LEGACY_RESULT\"}");
        }
        EodRunStepEntity failed = run.requireStep("FIXED_DEPOSIT_ACCRUALS");
        failed.markRunning();
        run.markFailed(failed, "DEPOSIT_NOT_READY", "Legacy FD failure", "{}");
        runs.saveAndFlush(run);
        peers.paymentsOpen = true; // Mirrors the live run that was manually reopened outside EOD.

        service.recoverInterruptedRuns();
        var guarded = service.get(run.id());

        assertThat(peers.codes()).isEmpty();
        assertThat(peers.paymentsOpen).isTrue();
        assertThat(guarded.steps()).filteredOn(step -> step.stepCode().equals("PAYMENTS_REOPEN"))
                .singleElement().satisfies(step -> {
                    assertThat(step.status()).isEqualTo("FAILED");
                    assertThat(step.errorCode()).isEqualTo("RECOVERY_FENCE_REQUIRED");
                });

        service.resume(run.id(), new EodResumeRequest("bank-admin", "Re-establish the payment fence"), null);
        awaitCompleted(run.id());

        assertThat(peers.codes()).startsWith("ACCOUNTING_PERIOD_OPEN_CURRENT", "PAYMENTS_CUTOFF",
                "PAYMENTS_DRAIN", "FIXED_DEPOSIT_ACCRUALS").endsWith("PAYMENTS_REOPEN");
        assertThat(peers.epochsFor("PAYMENTS_CUTOFF")).containsExactly(2);
        assertThat(peers.epochsFor("PAYMENTS_DRAIN")).containsExactly(2);
        assertThat(peers.paymentsOpen).isTrue();
    }

    @Test
    void recoveryFinalizesAnAlreadyReopenedRunWithoutCallingAnotherPeer() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_NEXT", 14, "accounting-service", "POST",
                        "/legacy/open-next"),
                new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = new EodRunEntity("reopened-crash-run", "reopened-crash-key", DATE,
                "operations-user", definitions);
        run.markRunning();
        for (EodRunStepEntity step : run.steps()) {
            step.markRunning();
            step.markCompleted("{\"status\":\"COMPLETED\"}");
        }
        runs.saveAndFlush(run);
        EodBusinessDateEntity date = new EodBusinessDateEntity(DATE);
        date.prepareNextDate(DATE.plusDays(1));
        businessDates.saveAndFlush(date);

        service.recoverInterruptedRuns();

        assertThat(service.get(run.id()).status()).isEqualTo("COMPLETED");
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE.plusDays(1));
        assertThat(service.businessDate().status()).isEqualTo("OPEN");
        assertThat(peers.codes()).isEmpty();
        assertThat(actions.findAllByRunIdOrderByCreatedAtAsc(run.id()))
                .extracting(EodRunActionEntity::actionType).contains("RECOVERY_COMPLETE");
    }

    @Test
    void staleLeaseLoserCannotFailOrFinalizeTheSuccessorWorkersStep() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST",
                        "/legacy/fd-accruals"),
                new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = new EodRunEntity("lease-fence-run", "lease-fence-key", DATE,
                "operations-user", definitions);
        run.markRunning();
        EodRunStepEntity step = run.requireStep("FIXED_DEPOSIT_ACCRUALS");
        step.markRunning("worker-a", java.time.OffsetDateTime.now().minusSeconds(1));
        step.markRunning("worker-b", java.time.OffsetDateTime.now().plusMinutes(1));
        runs.saveAndFlush(run);

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,
                "bestEffortUnexpectedFailure", run.id(), step.code(), "worker-a",
                new EodConflictException("worker-a lost its lease"));
        Boolean held = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,
                "holdPaymentsForFinancialRecovery", run.id(), "worker-a");

        assertThat(held).isFalse();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EodRunEntity persisted = runs.findById(run.id()).orElseThrow();
            EodRunStepEntity current = persisted.requireStep("FIXED_DEPOSIT_ACCRUALS");
            assertThat(current.status()).isEqualTo("RUNNING");
            assertThat(current.executionToken()).isEqualTo("worker-b");
            assertThat(persisted.requireStep("PAYMENTS_REOPEN").status()).isEqualTo("PENDING");
            assertThat(persisted.exceptions()).isEmpty();
        });
    }

    @Test
    void unexpectedDependencyStateIsPersistedAndStillRunsTheSafetyFinalizer() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("DEPOSIT_READINESS", 1, "deposit-account-service", "GET", "/legacy/ready",
                        List.of("MISSING_DEPENDENCY"), StepExecutionMode.REQUIRED, StepAuthMode.AUTO,
                        1, 0, "CORRUPT-SNAPSHOT", ""),
                new StepDefinition("PAYMENTS_REOPEN", 2, "payments-service", "POST", "/legacy/reopen"));
        EodRunEntity run = runs.saveAndFlush(new EodRunEntity("unexpected-state-run", "unexpected-state-key",
                DATE, "operations-user", "CORRUPT-SNAPSHOT", definitions));

        service.resume(run.id());
        var failed = awaitFailedWithFinalizer(run.id());

        assertThat(failed.exceptions()).anySatisfy(exception ->
                assertThat(exception.errorCode()).isEqualTo("ORCHESTRATION_EXCEPTION"));
        assertThat(peers.codes()).containsExactly("PAYMENTS_REOPEN");
    }

    @Test
    void manualBusinessDateAdvanceRejectsRunningAndFailedEod() {
        EodBusinessDateEntity current = new EodBusinessDateEntity(DATE);
        current.startEod();
        businessDates.saveAndFlush(current);

        assertThatThrownBy(() -> service.openNext(new EodController.OpenBusinessDateRequest(
                DATE.plusDays(1), "bank-admin"))).isInstanceOf(EodConflictException.class);

        current.markFailed();
        businessDates.saveAndFlush(current);
        assertThatThrownBy(() -> service.openNext(new EodController.OpenBusinessDateRequest(
                DATE.plusDays(1), "bank-admin"))).isInstanceOf(EodConflictException.class);
    }

    @Test
    void manualBusinessDateAdvanceIsIdempotentButCannotDoubleAdvanceACompletedRun() {
        var started = service.start("date-advance-key", new StartEodRunRequest(DATE, "operations-user"));
        awaitCompleted(started.runId());

        var idempotent = service.openNext(new EodController.OpenBusinessDateRequest(
                DATE.plusDays(1), "bank-admin"));
        assertThat(idempotent.businessDate()).isEqualTo(DATE.plusDays(1));
        assertThatThrownBy(() -> service.openNext(new EodController.OpenBusinessDateRequest(
                DATE.plusDays(2), "bank-admin"))).isInstanceOf(EodConflictException.class);
    }

    @Test
    void rejectsAPersistedWorkflowWithMultipleSafetyFinalizersBeforeCallingPeers() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                        "/legacy/open"),
                new StepDefinition("PAYMENTS_REOPEN", 2, "payments-service", "POST", "/legacy/reopen"),
                new StepDefinition("NOTIFICATIONS_SEND", 3, "notification-service", "POST", "/legacy/notify",
                        List.of(), StepExecutionMode.ALWAYS_RUN, StepAuthMode.AUTO, 1, 0,
                        "CORRUPT-SNAPSHOT", ""));
        EodRunEntity run = runs.saveAndFlush(new EodRunEntity("corrupt-finalizer-run", "corrupt-finalizer-key",
                DATE, "operations-user", "CORRUPT-SNAPSHOT", definitions));

        service.resume(run.id());
        var failed = awaitTerminal(run.id());

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.errorCode()).isEqualTo("WORKFLOW_FINALIZER_INVALID"));
        assertThat(peers.codes()).isEmpty();
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE);
    }

    @Test
    void recoveryCannotCompleteOrAdvanceAnAllMainCompletedSnapshotWithoutPaymentFinalizer() {
        List<StepDefinition> definitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_NEXT", 14, "accounting-service", "POST",
                        "/legacy/open-next"));
        EodRunEntity run = new EodRunEntity("missing-finalizer-run", "missing-finalizer-key", DATE,
                "operations-user", "CORRUPT-SNAPSHOT", definitions);
        run.markRunning();
        EodRunStepEntity step = run.requireStep("ACCOUNTING_PERIOD_OPEN_NEXT");
        step.markRunning();
        step.markCompleted("{\"status\":\"OPEN\"}");
        runs.saveAndFlush(run);

        service.recoverInterruptedRuns();

        var failed = service.get(run.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.errorCode()).isEqualTo("WORKFLOW_FINALIZER_INVALID"));
        assertThat(service.businessDate().businessDate()).isEqualTo(DATE);
        assertThat(service.businessDate().status()).isEqualTo("EOD_FAILED");
        assertThat(peers.codes()).isEmpty();
    }

    @Test
    void resumesUsingThePersistedLegacyStepOrderAndContracts() {
        List<StepDefinition> persistedDefinitions = List.of(
                new StepDefinition("FIXED_DEPOSIT_READINESS", 1, "deposit-account-service", "GET",
                        "/legacy/deposits/eod/fixed-deposit-readiness"),
                new StepDefinition("FIXED_DEPOSIT_ACCRUALS", 2, "deposit-account-service", "POST",
                        "/legacy/deposits/eod/fixed-deposit-accruals"),
                new StepDefinition("STATEMENTS_GENERATE", 3, "statements-service", "POST",
                        "/legacy/statements/eod/generate"),
                new StepDefinition("PAYMENTS_REOPEN", 4, "payments-service", "POST",
                        "/legacy/payments/eod/reopen"));
        EodRunEntity run = runs.saveAndFlush(new EodRunEntity("legacy-resume-run", "legacy-resume-key",
                DATE, "operations-user", persistedDefinitions));

        service.resume(run.id());

        var completed = awaitCompleted(run.id());
        assertThat(completed.steps()).extracting(EodController.StepResponse::stepCode)
                .containsExactly("FIXED_DEPOSIT_READINESS", "FIXED_DEPOSIT_ACCRUALS",
                        "STATEMENTS_GENERATE", "PAYMENTS_REOPEN");
        assertThat(peers.invoked).containsExactly(
                "GET /legacy/deposits/eod/fixed-deposit-readiness",
                "POST /legacy/deposits/eod/fixed-deposit-accruals",
                "POST /legacy/statements/eod/generate",
                "POST /legacy/payments/eod/reopen");
    }

    @Test
    void retriesAnOmittedLegacyStepByItsPersistedCode() {
        List<StepDefinition> persistedDefinitions = List.of(
                new StepDefinition("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                        "/legacy/accounting/open"),
                new StepDefinition("FIXED_DEPOSIT_READINESS", 2, "deposit-account-service", "GET",
                        "/legacy/deposits/eod/fixed-deposit-readiness"),
                new StepDefinition("PAYMENTS_REOPEN", 3, "payments-service", "POST",
                        "/legacy/payments/eod/reopen"));
        EodRunEntity run = new EodRunEntity("legacy-retry-run", "legacy-retry-key", DATE,
                "operations-user", persistedDefinitions);
        EodRunStepEntity completedStep = run.requireStep("ACCOUNTING_PERIOD_OPEN_CURRENT");
        completedStep.markRunning();
        completedStep.markCompleted("{\"status\":\"OPEN\"}");
        EodRunStepEntity failedStep = run.requireStep("FIXED_DEPOSIT_READINESS");
        failedStep.markRunning();
        run.markFailed(failedStep, "DEPOSIT_NOT_READY", "Active reservations remain", "{}");
        runs.saveAndFlush(run);

        service.retry(run.id(), "fixed_deposit_readiness");

        var completed = awaitCompleted(run.id());
        assertThat(peers.invoked).containsExactly(
                "POST /legacy/accounting/open",
                "GET /legacy/deposits/eod/fixed-deposit-readiness",
                "POST /legacy/payments/eod/reopen");
        assertThat(completed.steps().get(1).attemptCount()).isEqualTo(2);
        assertThat(completed.exceptions()).singleElement().satisfies(exception ->
                assertThat(exception.status()).isEqualTo("RESOLVED"));
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

    private EodController.EodRunResponse awaitFailedWithFinalizer(String runId) {
        for (int attempt = 0; attempt < 300; attempt++) {
            var response = service.get(runId);
            boolean reopened = response.steps().stream()
                    .anyMatch(step -> step.stepCode().equals("PAYMENTS_REOPEN")
                            && step.status().equals("COMPLETED"));
            if ("FAILED".equals(response.status()) && reopened) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the EOD finalizer", exception);
            }
        }
        throw new AssertionError("Failed EOD run did not reopen Payments");
    }

    private EodController.EodRunResponse awaitHeldForRecovery(String runId) {
        for (int attempt = 0; attempt < 300; attempt++) {
            var response = service.get(runId);
            boolean held = response.steps().stream()
                    .anyMatch(step -> step.stepCode().equals("PAYMENTS_REOPEN")
                            && step.status().equals("FAILED")
                            && "HELD_FOR_EOD_RECOVERY".equals(step.errorCode()));
            if ("FAILED".equals(response.status()) && held) return response;
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the EOD payment fence", exception);
            }
        }
        throw new AssertionError("Failed EOD run did not hold the payment fence");
    }

    @TestConfiguration
    static class StubConfiguration {
        @Bean
        @Primary
        StubPeerOperations stubPeerOperations() { return new StubPeerOperations(); }
    }

    static final class StubPeerOperations implements PeerOperations {
        private final List<String> invoked = new ArrayList<>();
        private final List<Invocation> invocations = new ArrayList<>();
        private final Map<String, Integer> remainingFailures = new HashMap<>();
        private final Map<String, String> failureCodes = new HashMap<>();
        private volatile boolean paymentsOpen = true;

        void reset() {
            invoked.clear();
            invocations.clear();
            remainingFailures.clear();
            failureCodes.clear();
            paymentsOpen = true;
        }

        void failOnceAt(String stepCode) { failAt(stepCode, "DEPOSIT_NOT_READY", 1); }

        void failAt(String stepCode, String errorCode, int times) {
            remainingFailures.put(stepCode, times);
            failureCodes.put(stepCode, errorCode);
        }

        List<String> codes() { return invocations.stream().map(Invocation::stepCode).toList(); }

        List<Integer> epochsFor(String stepCode) {
            return invocations.stream().filter(value -> value.stepCode().equals(stepCode))
                    .map(Invocation::executionEpoch).toList();
        }

        @Override
        public Map<String, Object> execute(StepDefinition step, EodContext context,
                                           Map<String, Map<String, Object>> outputs) {
            invoked.add(step.method() + " " + step.path());
            invocations.add(new Invocation(step.code(), context.executionEpoch()));
            int remaining = remainingFailures.getOrDefault(step.code(), 0);
            if (remaining > 0) {
                remainingFailures.put(step.code(), remaining - 1);
                throw new PeerOperationException(failureCodes.get(step.code()), "Configured test failure",
                        Map.of("ready", false));
            }
            String owner = "EOD:" + context.runId() + ":PAYMENTS_BARRIER:EPOCH:"
                    + context.executionEpoch();
            if ("PAYMENTS_CUTOFF".equals(step.code())) {
                paymentsOpen = false;
                return Map.of("status", "CUT_OFF", "newPaymentIntake", false,
                        "businessDate", context.businessDate().toString(), "currencyCode", context.currency(),
                        "commandReference", owner);
            }
            if ("PAYMENTS_DRAIN".equals(step.code())) return Map.of(
                    "status", "DRAINED", "pendingPayments", 0, "newPaymentIntake", false,
                    "businessDate", context.businessDate().toString(), "currencyCode", context.currency(),
                    "commandReference", owner);
            if ("PAYMENTS_REOPEN".equals(step.code())) {
                paymentsOpen = true;
                LocalDate targetDate = outputs.containsKey("ACCOUNTING_PERIOD_OPEN_NEXT")
                        ? context.businessDate().plusDays(1) : context.businessDate();
                return Map.of("status", "OPEN", "newPaymentIntake", true,
                        "businessDate", targetDate.toString(), "currencyCode", context.currency(),
                        "commandReference", owner);
            }
            return Map.of("source", step.providerService(), "step", step.code());
        }

        private record Invocation(String stepCode, int executionEpoch) {}
    }
}
