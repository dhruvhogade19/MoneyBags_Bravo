package com.moneybags.eod.port;

import com.moneybags.eod.domain.EodDomain.StepDefinition;
import java.time.LocalDate;
import java.util.Map;

public interface PeerOperations {
    record Request(String eodRunId, LocalDate businessDate, String commandReference,
                   Map<String, Object> body, Map<String, String> headers) {}
    record Result(boolean successful, String code, String message, Map<String, Object> payload) {}
    Result execute(StepDefinition step, Request request);
    Result openAccountingPeriod(LocalDate businessDate, String requestedBy, String commandReference);
}
