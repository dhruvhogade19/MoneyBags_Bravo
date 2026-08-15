package com.moneybags.creditcard.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubAccountingLifecycleGateway implements AccountingLifecycleGateway {
    @Override
    public LifecycleResponse publishOpening(AccountOpenedEvent event) {
        return new LifecycleResponse("OPEN");
    }

    @Override
    public ClearanceResponse clearance(String accountReference) {
        return new ClearanceResponse(true, List.of());
    }

    @Override
    public LifecycleResponse publishClosure(AccountClosedEvent event) {
        return new LifecycleResponse("CLOSED");
    }
}
