package com.moneybags.creditcard.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "true", matchIfMissing = true)
public class StubNotificationGateway implements NotificationGateway {
    @Override
    public void sendAccountCreated(AccountCreatedNotification notification) {
        // Local stub deliberately performs no external delivery.
    }
}
