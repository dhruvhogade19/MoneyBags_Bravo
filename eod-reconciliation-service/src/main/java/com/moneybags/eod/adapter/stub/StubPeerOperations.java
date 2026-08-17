package com.moneybags.eod.adapter.stub;

import com.moneybags.eod.config.EodProperties;
import com.moneybags.eod.domain.EodDomain.StepDefinition;
import com.moneybags.eod.port.PeerOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "moneybags.eod.stub-peer-clients", havingValue = "true", matchIfMissing = true)
public class StubPeerOperations implements PeerOperations {
    private final EodProperties properties;
    public StubPeerOperations(EodProperties properties) { this.properties = properties; }

    @Override
    public Result execute(StepDefinition step, Request request) {
        if (properties.getStubFailOn().stream().anyMatch(value -> value.equalsIgnoreCase(step.name()))) {
            return new Result(false, "DUMMY_" + step.name() + "_FAILED",
                    "Configured dummy failure for " + step.name(), Map.of("retryable", true));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (step) {
            case DEPOSIT_READINESS -> {
                payload.put("service", "deposit-account-service");
                payload.put("businessDate", request.businessDate());
                payload.put("ready", true);
                payload.put("blockers", List.of());
            }
            case DEPOSIT_ACCRUALS -> {
                payload.put("eodRunId", request.eodRunId());
                payload.put("commandReference", request.commandReference());
                payload.put("businessDate", request.businessDate());
                payload.put("processedCount", 1250);
                payload.put("failedCount", 0);
                payload.put("totalAmount", new BigDecimal("54231.18"));
                payload.put("failures", List.of());
            }
            case FD_INTEREST_ACCRUAL -> {
                payload.put("eodRunId", request.eodRunId());
                payload.put("businessDate", request.businessDate());
                payload.put("commandReference", request.commandReference());
                payload.put("processed", 750);
                payload.put("skipped", 4);
                payload.put("totalAmount", new BigDecimal("18450.72"));
                payload.put("failures", List.of());
            }
            case FD_MATURITY_PROCESSING -> {
                payload.put("eodRunId", request.eodRunId());
                payload.put("businessDate", request.businessDate());
                payload.put("commandReference", request.commandReference());
                payload.put("processed", 8);
                payload.put("skipped", 0);
                payload.put("totalAmount", new BigDecimal("1245000.00"));
                payload.put("failures", List.of());
            }
            case FD_ACCOUNTING_RECONCILIATION -> {
                payload.put("matched", true);
                payload.put("unmatchedItems", 0);
            }
            case FD_READINESS_CHECK -> {
                payload.put("ready", true);
                payload.put("pendingFunding", 0);
                payload.put("pendingPayouts", 0);
                payload.put("blockers", List.of());
            }
            default -> {
                payload.put("dummy", true);
                payload.put("provider", step.service());
                payload.put("eodRunId", request.eodRunId());
                payload.put("businessDate", request.businessDate());
                payload.put("commandReference", request.commandReference());
                switch (step) {
                    case PAYMENTS_DRAIN -> payload.put("pendingPayments", 0);
                    case CREDIT_CARD_READINESS -> payload.put("readyAccounts", 128);
                    case BILL_CLOSE -> payload.put("billsGenerated", 47);
                    case TRIAL_BALANCE -> { payload.put("balanced", true); payload.put("totalDebit", new BigDecimal("2750000.00")); }
                    case FINANCIAL_RECONCILIATION -> payload.put("unmatchedItems", 0);
                    case STATEMENT_GENERATION -> payload.put("statementsGenerated", 215);
                    case EOD_NOTIFICATION -> payload.put("notificationsAccepted", 6);
                    default -> payload.put("accepted", true);
                }
            }
        }
        return new Result(true, "OK", "Dummy " + step.name() + " completed", payload);
    }

    @Override
    public Result openAccountingPeriod(LocalDate date, String requestedBy, String commandReference) {
        return new Result(true, "OK", "Dummy accounting period opened",
                Map.of("dummy", true, "businessDate", date, "openedBy", requestedBy, "commandReference", commandReference));
    }
}
