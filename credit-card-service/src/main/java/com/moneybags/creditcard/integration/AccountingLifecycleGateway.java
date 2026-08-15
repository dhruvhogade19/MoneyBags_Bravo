package com.moneybags.creditcard.integration;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public interface AccountingLifecycleGateway {
    LifecycleResponse publishOpening(AccountOpenedEvent event);

    ClearanceResponse clearance(String accountReference);

    LifecycleResponse publishClosure(AccountClosedEvent event);

    record AccountOpenedEvent(String eventReference, String eventType, String accountType, String accountReference,
                              String productCode, String currencyCode, LocalDate businessDate,
                              OffsetDateTime occurredAt) {
    }

    record AccountClosedEvent(String eventReference, String eventType, String accountType, String accountReference,
                              String currencyCode, LocalDate businessDate, OffsetDateTime occurredAt,
                              String reasonCode) {
    }

    record LifecycleResponse(String accountingLifecycleState) {
    }

    record ClearanceResponse(boolean accountingCleared, List<String> blockers) {
    }
}
