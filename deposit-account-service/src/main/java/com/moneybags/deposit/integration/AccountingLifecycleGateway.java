package com.moneybags.deposit.integration;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Contract between Deposit Account Service and Accounting's account lifecycle API. */
public interface AccountingLifecycleGateway {
    LifecycleResponse publishOpening(AccountOpenedEvent event, String idempotencyKey, String correlationId);
    ClearanceResponse clearance(String accountReference, String currencyCode);
    LifecycleResponse publishClosure(AccountClosedEvent event, String idempotencyKey, String correlationId);

    record AccountOpenedEvent(String eventReference, String eventType, String accountType, String accountReference,
                              String productCode, String currencyCode, LocalDate businessDate,
                              OffsetDateTime occurredAt) { }
    record AccountClosedEvent(String eventReference, String eventType, String accountType, String accountReference,
                              String currencyCode, LocalDate businessDate, OffsetDateTime occurredAt,
                              String reasonCode) { }
    record LifecycleResponse(String accountingLifecycleState) { }
    record ClearanceResponse(boolean accountingCleared, List<String> blockers) { }
}
