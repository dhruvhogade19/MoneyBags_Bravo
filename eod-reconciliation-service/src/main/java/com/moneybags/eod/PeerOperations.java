package com.moneybags.eod;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;

interface PeerOperations {
    Map<String, Object> execute(StepDefinition step, EodContext context, Map<String, Map<String, Object>> outputs);
}

record StepDefinition(String code,
                      int sequence,
                      String providerService,
                      String method,
                      String path,
                      List<String> dependencies,
                      StepExecutionMode executionMode,
                      StepAuthMode authMode,
                      int maxAttempts,
                      long retryBackoffMs,
                      String contractVersion,
                      String idempotencySuffix) {
    StepDefinition {
        code = required(code, "code").toUpperCase(Locale.ROOT);
        if (sequence < 1) throw new IllegalArgumentException("EOD step sequence must be positive");
        providerService = required(providerService, "providerService");
        method = required(method, "method").toUpperCase(Locale.ROOT);
        path = required(path, "path");
        dependencies = dependencies == null ? List.of() : dependencies.stream()
                .map(value -> required(value, "dependency").toUpperCase(Locale.ROOT))
                .distinct().toList();
        executionMode = Objects.requireNonNullElse(executionMode, StepExecutionMode.REQUIRED);
        authMode = Objects.requireNonNullElse(authMode, StepAuthMode.AUTO);
        if (maxAttempts < 1) throw new IllegalArgumentException("EOD maxAttempts must be positive");
        if (retryBackoffMs < 0) throw new IllegalArgumentException("EOD retryBackoffMs cannot be negative");
        contractVersion = required(contractVersion, "contractVersion");
        idempotencySuffix = idempotencySuffix == null ? "" : idempotencySuffix.trim();
    }

    /** Compatibility constructor for step snapshots created before workflow metadata was introduced. */
    StepDefinition(String code, int sequence, String providerService, String method, String path) {
        this(code, sequence, providerService, method, path, List.of(),
                "PAYMENTS_REOPEN".equalsIgnoreCase(code)
                        ? StepExecutionMode.ALWAYS_RUN : StepExecutionMode.REQUIRED,
                StepAuthMode.AUTO, 1, 0, "LEGACY-V1",
                "FIXED_DEPOSIT_RECONCILIATION".equalsIgnoreCase(code)
                        ? "JOURNAL-CORRELATED-V2" : "");
    }

    boolean finalizer() { return executionMode == StepExecutionMode.ALWAYS_RUN; }
    boolean optional() { return executionMode == StepExecutionMode.OPTIONAL; }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("EOD step " + field + " is required");
        return value.trim();
    }
}

enum StepExecutionMode { REQUIRED, OPTIONAL, ALWAYS_RUN }
enum StepAuthMode { AUTO, SERVICE, OPERATOR }

record EodContext(String runId, LocalDate businessDate, String actorId, String currency,
                  String operatorAuthorization, int executionEpoch, String commandReference) {
    EodContext(String runId, LocalDate businessDate, String actorId, String currency,
               String operatorAuthorization) {
        this(runId, businessDate, actorId, currency, operatorAuthorization, 1, null);
    }
}

class PeerOperationException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;

    PeerOperationException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNullElse(code, "UPSTREAM_ERROR");
        this.details = details == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    String code() { return code; }
    Map<String, Object> details() { return details; }
}
