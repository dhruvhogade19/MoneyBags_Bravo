package com.moneybags.deposit.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubAccountingLifecycleGateway implements AccountingLifecycleGateway {
    @Override public LifecycleResponse publishOpening(AccountOpenedEvent event, String key, String correlationId) { return new LifecycleResponse("OPEN"); }
    @Override public ClearanceResponse clearance(String accountReference, String currencyCode) { return new ClearanceResponse(true, List.of()); }
    @Override public LifecycleResponse publishClosure(AccountClosedEvent event, String key, String correlationId) { return new LifecycleResponse("CLOSED"); }
}
