package com.moneybags.deposit.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class RestClientAccountingLifecycleGateway implements AccountingLifecycleGateway {
    private final RestClient accounting;

    public RestClientAccountingLifecycleGateway(RestClient.Builder builder,
                                                @Value("${moneybags.clients.accounting.base-url}") String baseUrl) {
        this.accounting = builder.baseUrl(baseUrl).build();
    }

    @Override
    public LifecycleResponse publishOpening(AccountOpenedEvent event, String key, String correlationId) {
        return accounting.post().uri("/internal/v1/account-lifecycle-events")
                .headers(headers -> headers(headers, key, correlationId)).body(event)
                .retrieve().body(LifecycleResponse.class);
    }

    @Override
    public ClearanceResponse clearance(String accountReference, String currencyCode) {
        return accounting.get().uri("/internal/v1/account-clearances/DEPOSIT_ACCOUNT/{accountReference}?currencyCode={currencyCode}",
                        accountReference, currencyCode).retrieve().body(ClearanceResponse.class);
    }

    @Override
    public LifecycleResponse publishClosure(AccountClosedEvent event, String key, String correlationId) {
        return accounting.post().uri("/internal/v1/account-lifecycle-events")
                .headers(headers -> headers(headers, key, correlationId)).body(event)
                .retrieve().body(LifecycleResponse.class);
    }

    private void headers(HttpHeaders headers, String key, String correlationId) {
        headers.set("Idempotency-Key", key);
        headers.set("X-Correlation-Id", correlationId);
    }
}
