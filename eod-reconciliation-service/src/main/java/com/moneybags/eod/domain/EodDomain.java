package com.moneybags.eod.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EodDomain {
    private EodDomain() {}

    public enum BusinessDateStatus { OPEN, CUTOFF, CLOSED }
    public enum RunStatus { RUNNING, BLOCKED, COMPLETED }
    public enum StepStatus { PENDING, RUNNING, COMPLETED, FAILED }
    public enum ExceptionStatus { OPEN, RESOLVED, WAIVED }

    public enum StepDefinition {
        PAYMENTS_CUTOFF(10, "payments-service", "POST", "/internal/v1/payments/eod/cutoff"),
        PAYMENTS_DRAIN(20, "payments-service", "POST", "/internal/v1/payments/eod/drain"),
        CREDIT_CARD_READINESS(30, "credit-card-service", "GET", "/internal/v1/credit-card-accounts/eod/readiness"),
        DEPOSIT_READINESS(40, "deposit-account-service", "GET", "/internal/v1/deposit-accounts/eod/readiness"),
        DEPOSIT_ACCRUALS(50, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/accruals"),
        FD_INTEREST_ACCRUAL(51, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/fixed-deposit-accruals"),
        FD_MATURITY_PROCESSING(52, "deposit-account-service", "POST", "/internal/v1/deposit-accounts/eod/fixed-deposit-maturities"),
        FD_ACCOUNTING_RECONCILIATION(53, "accounting-service", "POST", "/internal/v1/accounting/fixed-deposit-reconciliation"),
        FD_READINESS_CHECK(54, "deposit-account-service", "GET", "/internal/v1/deposit-accounts/eod/fixed-deposit-readiness"),
        BILL_CLOSE(60, "bill-generation-service", "POST", "/internal/v1/bills/eod/close"),
        TRIAL_BALANCE(70, "accounting-service", "POST", "/internal/v1/trial-balances"),
        FINANCIAL_RECONCILIATION(80, "accounting-service", "POST", "/internal/v1/eod/reconciliation/runs"),
        STATEMENT_GENERATION(90, "statement-service", "POST", "/internal/v1/statements/eod/generate"),
        EOD_NOTIFICATION(100, "notification-service", "POST", "/internal/v1/notifications"),
        ACCOUNTING_PERIOD_CLOSE(110, "accounting-service", "POST", "/internal/v1/accounting-periods/{businessDate}/close");

        private final int sequence;
        private final String service;
        private final String method;
        private final String path;

        StepDefinition(int sequence, String service, String method, String path) {
            this.sequence = sequence;
            this.service = service;
            this.method = method;
            this.path = path;
        }
        public int sequence() { return sequence; }
        public String service() { return service; }
        public String method() { return method; }
        public String path() { return path; }
        public boolean requestBodyRequired() { return "POST".equals(method); }
        public boolean idempotencyKeyRequired() {
            return this == FD_INTEREST_ACCRUAL || this == FD_MATURITY_PROCESSING;
        }
    }

    public static final class BusinessDateState {
        private final LocalDate businessDate;
        private BusinessDateStatus status;
        private Instant cutoffAt;
        private Instant openedAt;
        private Instant closedAt;
        private long version;

        public BusinessDateState(LocalDate businessDate, Instant openedAt) {
            this.businessDate = businessDate;
            this.openedAt = openedAt;
            this.status = BusinessDateStatus.OPEN;
        }
        public LocalDate businessDate() { return businessDate; }
        public BusinessDateStatus status() { return status; }
        public Instant cutoffAt() { return cutoffAt; }
        public Instant openedAt() { return openedAt; }
        public Instant closedAt() { return closedAt; }
        public long version() { return version; }
        public void cutoff(Instant at) { status = BusinessDateStatus.CUTOFF; cutoffAt = at; version++; }
        public void close(Instant at) { status = BusinessDateStatus.CLOSED; closedAt = at; version++; }
    }

    public static final class EodStep {
        private final StepDefinition definition;
        private final String commandReference;
        private StepStatus status = StepStatus.PENDING;
        private int attemptCount;
        private Instant startedAt;
        private Instant completedAt;
        private String errorCode;
        private String message;
        private Map<String, Object> output = Map.of();

        public EodStep(StepDefinition definition, String runId, LocalDate businessDate) {
            this.definition = definition;
            String compactDate = businessDate.format(DateTimeFormatter.BASIC_ISO_DATE);
            this.commandReference = switch (definition) {
                case DEPOSIT_ACCRUALS -> "DEP-ACCRUAL-" + compactDate + "-V1";
                case FD_INTEREST_ACCRUAL -> "FD-ACCRUAL-" + compactDate + "-V1";
                case FD_MATURITY_PROCESSING -> "FD-MATURITY-" + compactDate + "-V1";
                case FD_ACCOUNTING_RECONCILIATION -> "FD-RECONCILIATION-" + compactDate + "-V1";
                case FD_READINESS_CHECK -> "FD-READINESS-" + compactDate + "-V1";
                default -> runId + ":" + definition.name();
            };
        }
        public StepDefinition definition() { return definition; }
        public String commandReference() { return commandReference; }
        public StepStatus status() { return status; }
        public int attemptCount() { return attemptCount; }
        public Instant startedAt() { return startedAt; }
        public Instant completedAt() { return completedAt; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public Map<String, Object> output() { return output; }
        public void start(Instant at) { status = StepStatus.RUNNING; attemptCount++; startedAt = at; errorCode = null; message = null; }
        public void complete(Instant at, String message, Map<String, Object> output) {
            status = StepStatus.COMPLETED; completedAt = at; this.message = message;
            this.output = output == null ? Map.of() : new LinkedHashMap<>(output);
        }
        public void fail(Instant at, String errorCode, String message, Map<String, Object> output) {
            status = StepStatus.FAILED; completedAt = at; this.errorCode = errorCode; this.message = message;
            this.output = output == null ? Map.of() : new LinkedHashMap<>(output);
        }
    }

    public static final class EodExceptionRecord {
        private final String exceptionId;
        private final String stepCode;
        private final String severity;
        private final String errorCode;
        private final Map<String, Object> details;
        private ExceptionStatus status = ExceptionStatus.OPEN;
        private String resolution;
        private String resolvedBy;
        private Instant resolvedAt;

        public EodExceptionRecord(String exceptionId, String stepCode, String errorCode, String message) {
            this.exceptionId = exceptionId; this.stepCode = stepCode; this.severity = "BLOCKING"; this.errorCode = errorCode;
            this.details = Map.of("message", message);
        }
        public String exceptionId() { return exceptionId; }
        public String stepCode() { return stepCode; }
        public String severity() { return severity; }
        public String errorCode() { return errorCode; }
        public Map<String, Object> details() { return details; }
        public ExceptionStatus status() { return status; }
        public String resolution() { return resolution; }
        public String resolvedBy() { return resolvedBy; }
        public Instant resolvedAt() { return resolvedAt; }
        public void resolve(String resolution, String resolvedBy, boolean waived, Instant at) {
            this.status = waived ? ExceptionStatus.WAIVED : ExceptionStatus.RESOLVED;
            this.resolution = resolution; this.resolvedBy = resolvedBy; this.resolvedAt = at;
        }
    }

    public static final class EodRun {
        private final String runId;
        private final LocalDate businessDate;
        private final String startedBy;
        private final Instant startedAt;
        private final List<EodStep> steps = new ArrayList<>();
        private final List<EodExceptionRecord> exceptions = new ArrayList<>();
        private RunStatus status = RunStatus.RUNNING;
        private Instant completedAt;
        private long version;

        public EodRun(String runId, LocalDate businessDate, String startedBy, Instant startedAt) {
            this.runId = runId; this.businessDate = businessDate; this.startedBy = startedBy; this.startedAt = startedAt;
            for (StepDefinition definition : StepDefinition.values()) steps.add(new EodStep(definition, runId, businessDate));
        }
        public String runId() { return runId; }
        public LocalDate businessDate() { return businessDate; }
        public String startedBy() { return startedBy; }
        public Instant startedAt() { return startedAt; }
        public List<EodStep> steps() { return steps; }
        public List<EodExceptionRecord> exceptions() { return exceptions; }
        public RunStatus status() { return status; }
        public Instant completedAt() { return completedAt; }
        public long version() { return version; }
        public void touch() { version++; }
        public void block() { status = RunStatus.BLOCKED; version++; }
        public void resume() { status = RunStatus.RUNNING; version++; }
        public void complete(Instant at) { status = RunStatus.COMPLETED; completedAt = at; version++; }
    }
}
