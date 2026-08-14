package com.moneybags.creditcard.integration;

import java.util.Map;

public interface NotificationGateway {
    void sendAccountCreated(AccountCreatedNotification notification);

    record AccountCreatedNotification(Long cifId, String notificationType, String sourceReference,
                                      Map<String, String> templateVariables) {
    }
}
