package com.moneybags.deposit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moneybags.deposit")
public record DepositAccountProperties(
        String accountNumberPrefix,
        int accountNumberRandomDigits,
        boolean stubUpstreamClients,
        int idempotencyTtlHours
) {
    public DepositAccountProperties {
        if (accountNumberPrefix == null || accountNumberPrefix.isBlank()) accountNumberPrefix = "MB";
        if (accountNumberRandomDigits < 8) accountNumberRandomDigits = 12;
        if (idempotencyTtlHours < 1) idempotencyTtlHours = 24;
    }
}
