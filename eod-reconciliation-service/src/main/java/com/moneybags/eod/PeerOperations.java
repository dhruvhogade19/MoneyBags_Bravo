package com.moneybags.eod;

import java.time.LocalDate;
import java.util.Map;

interface PeerOperations {
    Map<String, Object> execute(StepDefinition step, EodContext context, Map<String, Map<String, Object>> outputs);
}

record StepDefinition(String code, int sequence, String providerService, String method, String path) {}
record EodContext(String runId, LocalDate businessDate, String actorId, String currency,
                  String operatorAuthorization) {}

class PeerOperationException extends RuntimeException {
    private final String code;
    private final Map<String, Object> details;

    PeerOperationException(String code, String message, Map<String, Object> details) {
        super(message); this.code = code; this.details = details;
    }

    String code() { return code; }
    Map<String, Object> details() { return details; }
}
