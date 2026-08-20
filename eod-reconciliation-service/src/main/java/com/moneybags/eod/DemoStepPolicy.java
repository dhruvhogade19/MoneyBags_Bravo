package com.moneybags.eod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class DemoStepPolicy {
    private static final Set<String> SAFE_DEMO_SKIPS = Set.of("STATEMENTS_GENERATE", "NOTIFICATIONS_SEND");
    private final boolean enabled;
    private final boolean allSteps;
    private final Set<String> skippedSteps;

    DemoStepPolicy(@Value("${moneybags.eod.demo.enabled:false}") boolean enabled,
                   @Value("${moneybags.eod.demo.skipped-steps:}") String skippedSteps,
                   Environment environment) {
        this.enabled = enabled && environment.acceptsProfiles(Profiles.of("demo"));
        Set<String> requestedSteps = Arrays.stream(skippedSteps.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.allSteps = this.enabled && requestedSteps.contains("*");
        this.skippedSteps = requestedSteps.stream()
                .filter(SAFE_DEMO_SKIPS::contains)
                .collect(Collectors.toUnmodifiableSet());
    }

    boolean skips(String stepCode) {
        return enabled && (allSteps || skippedSteps.contains(stepCode.toUpperCase(Locale.ROOT)));
    }

    boolean allStepsEnabled() { return allSteps; }

    Map<String, Object> output(String stepCode, EodContext context) {
        if (!allSteps) return skippedOutput(stepCode);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("demoMode", true);
        output.put("bypassed", true);
        output.put("controlBypassed", true);
        output.put("syntheticSuccess", true);
        output.put("stepCode", stepCode);
        output.put("reason", reason(stepCode));
        output.put("businessDate", context.businessDate().toString());
        output.put("currencyCode", context.currency());
        output.put("eodRunId", context.runId());

        switch (stepCode.toUpperCase(Locale.ROOT)) {
            case "ACCOUNTING_PERIOD_OPEN_CURRENT" -> output.put("status", "OPEN");
            case "PAYMENTS_CUTOFF" -> {
                output.put("status", "CUT_OFF");
                output.put("pendingPayments", 0);
                output.put("newPaymentIntake", false);
                output.put("commandReference", paymentBarrierReference(context));
            }
            case "PAYMENTS_DRAIN" -> {
                output.put("status", "DRAINED");
                output.put("pendingPayments", 0);
                output.put("newPaymentIntake", false);
                output.put("commandReference", paymentBarrierReference(context));
                output.put("postedJournalCount", 0);
                output.put("postedDebitTotal", 0);
            }
            case "CREDIT_CARD_READINESS" -> {
                output.put("status", "READY");
                output.put("ready", true);
                output.put("readyForEod", true);
                output.put("activeAccountCount", 0);
                output.put("blockedAccountCount", 0);
                output.put("pendingApplicationCount", 0);
                output.put("blockers", List.of());
                output.put("closureBlockers", List.of());
            }
            case "DEPOSIT_READINESS" -> {
                output.put("status", "READY");
                output.put("ready", true);
                output.put("blockers", List.of());
                output.put("depositAccounts", Map.of(
                        "service", "deposit-account-service",
                        "businessDate", context.businessDate().toString(),
                        "ready", true,
                        "blockers", List.of()));
                output.put("fixedDeposits", Map.of(
                        "ready", true,
                        "pendingFunding", 0,
                        "pendingPayouts", 0,
                        "blockers", List.of()));
            }
            case "FIXED_DEPOSIT_READINESS" -> {
                output.put("status", "READY");
                output.put("ready", true);
                output.put("pendingFunding", 0);
                output.put("pendingPayouts", 0);
                output.put("blockers", List.of());
            }
            case "DEPOSIT_ACCRUALS" -> {
                output.put("status", "COMPLETED");
                output.put("processed", 0);
                output.put("processedCount", 0);
                output.put("skipped", 0);
                output.put("failedCount", 0);
                output.put("failures", List.of());
                output.put("totalAmount", 0);
            }
            case "FIXED_DEPOSIT_ACCRUALS", "FIXED_DEPOSIT_MATURITIES" -> {
                output.put("status", "COMPLETED");
                output.put("processed", 0);
                output.put("skipped", 0);
                output.put("failures", List.of());
                output.put("totalAmount", 0);
                output.put("postedJournalCount", 0);
                output.put("postedDebitTotal", 0);
            }
            case "BILLS_CLOSE" -> {
                output.put("status", "COMPLETED");
                output.put("processed", 0);
                output.put("billsProcessed", 0);
                output.put("failedCount", 0);
                output.put("failures", List.of());
                output.put("pendingBillReferences", List.of());
            }
            case "TRIAL_BALANCE" -> {
                output.put("status", "BALANCED");
                output.put("runId", context.runId());
                output.put("balanced", true);
                output.put("difference", 0);
                output.put("totalDebit", 0);
                output.put("totalCredit", 0);
                output.put("lines", List.of());
            }
            case "PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION" -> {
                output.put("status", "MATCHED");
                output.put("runId", context.runId());
                output.put("expectedJournalCount", 0);
                output.put("actualJournalCount", 0);
                output.put("expectedTotalDebit", 0);
                output.put("actualTotalDebit", 0);
                output.put("difference", 0);
                output.put("items", List.of());
            }
            case "ACCOUNTING_PERIOD_CLOSE" -> output.put("status", "CLOSED");
            case "ACCOUNTING_PERIOD_OPEN_NEXT" -> {
                output.put("status", "OPEN");
                output.put("businessDate", context.businessDate().plusDays(1).toString());
            }
            case "PAYMENTS_REOPEN" -> {
                output.put("status", "OPEN");
                output.put("businessDate", context.businessDate().plusDays(1).toString());
                output.put("newPaymentIntake", true);
                output.put("pendingPayments", 0);
                output.put("postedJournalCount", 0);
                output.put("postedDebitTotal", 0);
                output.put("commandReference", paymentBarrierReference(context));
            }
            default -> {
                output.put("status", "COMPLETED");
                output.put("failedCount", 0);
            }
        }
        return Map.copyOf(output);
    }

    private Map<String, Object> skippedOutput(String stepCode) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "SKIPPED");
        output.put("demoMode", true);
        output.put("bypassed", true);
        output.put("controlBypassed", true);
        output.put("stepCode", stepCode);
        output.put("reason", reason(stepCode));
        return Map.copyOf(output);
    }

    private String paymentBarrierReference(EodContext context) {
        return "EOD:" + context.runId() + ":PAYMENTS_BARRIER:EPOCH:"
                + Math.max(context.executionEpoch(), 1);
    }

    String reason(String stepCode) {
        if (allSteps) return "Explicit all-step local demo bypass; no peer HTTP request was made";
        return switch (stepCode.toUpperCase(Locale.ROOT)) {
            case "STATEMENTS_GENERATE" ->
                    "Temporarily bypassed for demo: the persisted EOD statement-generation contract is unavailable";
            case "NOTIFICATIONS_SEND" ->
                    "Temporarily bypassed for demo because statement generation is disabled";
            default -> "Temporarily bypassed by the configured EOD demo policy";
        };
    }
}
