package com.moneybags.eod;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class EodWorkflowRegistry {
    static final String CURRENT_VERSION = "EOD-2026.2";
    private static final int STANDARD_ATTEMPTS = 3;
    private static final long STANDARD_BACKOFF_MS = 250;

    private static final List<StepDefinition> CURRENT_STEPS = List.of(
            required("ACCOUNTING_PERIOD_OPEN_CURRENT", 1, "accounting-service", "POST",
                    "/internal/v1/accounting-periods/{businessDate}/open", List.of(), StepAuthMode.SERVICE),
            required("PAYMENTS_CUTOFF", 2, "payments-service", "POST",
                    "/internal/v1/payments/eod/cutoff", List.of("ACCOUNTING_PERIOD_OPEN_CURRENT"), StepAuthMode.SERVICE),
            new StepDefinition("PAYMENTS_DRAIN", 3, "payments-service", "POST",
                    "/internal/v1/payments/eod/drain", List.of("PAYMENTS_CUTOFF"),
                    StepExecutionMode.REQUIRED, StepAuthMode.SERVICE, 9, 1_000,
                    "PAYMENTS-SERVICE-EOD-V1", ""),
            required("CREDIT_CARD_READINESS", 4, "credit-card-service", "GET",
                    "/internal/v1/credit-card-accounts/eod/readiness", List.of("PAYMENTS_DRAIN"), StepAuthMode.SERVICE),
            required("DEPOSIT_READINESS", 5, "deposit-account-service", "GET",
                    "/internal/v1/deposit-accounts/eod/operations-readiness", List.of("PAYMENTS_DRAIN"), StepAuthMode.SERVICE),
            required("DEPOSIT_ACCRUALS", 6, "deposit-account-service", "POST",
                    "/internal/v1/deposit-accounts/eod/accruals", List.of("DEPOSIT_READINESS"), StepAuthMode.SERVICE),
            required("FIXED_DEPOSIT_ACCRUALS", 7, "deposit-account-service", "POST",
                    "/internal/v1/deposit-accounts/eod/fixed-deposit-accruals", List.of("DEPOSIT_READINESS"), StepAuthMode.SERVICE),
            required("FIXED_DEPOSIT_MATURITIES", 8, "deposit-account-service", "POST",
                    "/internal/v1/deposit-accounts/eod/fixed-deposit-maturities", List.of("FIXED_DEPOSIT_ACCRUALS"), StepAuthMode.SERVICE),
            required("BILLS_CLOSE", 9, "bill-generation-service", "POST",
                    "/internal/v1/bills/eod/close", List.of("CREDIT_CARD_READINESS"), StepAuthMode.SERVICE),
            required("TRIAL_BALANCE", 10, "accounting-service", "POST",
                    "/internal/v1/trial-balances",
                    List.of("DEPOSIT_ACCRUALS", "FIXED_DEPOSIT_MATURITIES", "BILLS_CLOSE"), StepAuthMode.SERVICE),
            required("PAYMENTS_RECONCILIATION", 11, "accounting-service", "POST",
                    "/internal/v1/eod/reconciliation/runs", List.of("TRIAL_BALANCE", "PAYMENTS_DRAIN"), StepAuthMode.SERVICE),
            new StepDefinition("FIXED_DEPOSIT_RECONCILIATION", 12, "accounting-service", "POST",
                    "/internal/v1/eod/reconciliation/runs",
                    List.of("TRIAL_BALANCE", "FIXED_DEPOSIT_ACCRUALS", "FIXED_DEPOSIT_MATURITIES"),
                    StepExecutionMode.REQUIRED, StepAuthMode.SERVICE, STANDARD_ATTEMPTS,
                    STANDARD_BACKOFF_MS, "ACCOUNTING-RECONCILIATION-V2", "JOURNAL-CORRELATED-V2"),
            required("ACCOUNTING_PERIOD_CLOSE", 13, "accounting-service", "POST",
                    "/internal/v1/accounting-periods/{businessDate}/close",
                    List.of("PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION"), StepAuthMode.SERVICE),
            required("ACCOUNTING_PERIOD_OPEN_NEXT", 14, "accounting-service", "POST",
                    "/internal/v1/accounting-periods/{businessDate}/open", List.of("ACCOUNTING_PERIOD_CLOSE"), StepAuthMode.SERVICE),
            new StepDefinition("PAYMENTS_REOPEN", 15, "payments-service", "POST",
                    "/internal/v1/payments/eod/reopen", List.of(), StepExecutionMode.ALWAYS_RUN,
                    StepAuthMode.SERVICE, 5, STANDARD_BACKOFF_MS, "PAYMENTS-EOD-V1", "")
    );

    EodWorkflowRegistry() {
        validate(CURRENT_STEPS);
    }

    String currentVersion() { return CURRENT_VERSION; }
    List<StepDefinition> currentSteps() { return CURRENT_STEPS; }

    private static StepDefinition required(String code, int sequence, String service, String method,
                                           String path, List<String> dependencies, StepAuthMode authMode) {
        return new StepDefinition(code, sequence, service, method, path, dependencies,
                StepExecutionMode.REQUIRED, authMode, STANDARD_ATTEMPTS, STANDARD_BACKOFF_MS,
                service.toUpperCase() + "-EOD-V1", "");
    }

    static void validate(List<StepDefinition> steps) {
        if (steps == null || steps.isEmpty()) throw new IllegalStateException("The EOD workflow cannot be empty");
        Map<String, StepDefinition> byCode = new HashMap<>();
        Set<Integer> sequences = new HashSet<>();
        for (StepDefinition step : steps) {
            if (byCode.put(step.code(), step) != null)
                throw new IllegalStateException("Duplicate EOD step code: " + step.code());
            if (!sequences.add(step.sequence()))
                throw new IllegalStateException("Duplicate EOD step sequence: " + step.sequence());
        }
        for (StepDefinition step : steps) {
            for (String dependency : step.dependencies()) {
                StepDefinition dependencyStep = byCode.get(dependency);
                if (dependencyStep == null)
                    throw new IllegalStateException(step.code() + " depends on missing EOD step " + dependency);
                if (dependencyStep.sequence() >= step.sequence())
                    throw new IllegalStateException(step.code() + " has a non-prior dependency " + dependency);
                if (dependencyStep.finalizer())
                    throw new IllegalStateException(step.code() + " cannot depend on finalizer " + dependency);
            }
        }
        long finalizers = steps.stream().filter(StepDefinition::finalizer).count();
        if (finalizers != 1 || steps.stream().noneMatch(step -> "PAYMENTS_REOPEN".equals(step.code()) && step.finalizer()))
            throw new IllegalStateException("PAYMENTS_REOPEN must be the single EOD always-run finalizer");
    }
}
