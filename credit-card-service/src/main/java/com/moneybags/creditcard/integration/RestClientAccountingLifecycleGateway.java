package com.moneybags.creditcard.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "false")
public class RestClientAccountingLifecycleGateway implements AccountingLifecycleGateway {
    private final RestClient accounting;

    public RestClientAccountingLifecycleGateway(RestClient.Builder builder,
                                                @Value("${moneybags.credit-card.accounting-base-url}") String accountingUrl) {
        accounting = builder.baseUrl(accountingUrl).build();
    }

    @Override
    public LifecycleResponse publishOpening(AccountOpenedEvent event) {
        return accounting.post().uri("/internal/v1/account-lifecycle-events")
                .headers(headers -> applyHeaders(headers, "CARD-OPEN:" + event.accountReference()))
                .body(event).retrieve().body(LifecycleResponse.class);
    }

    @Override
    public ClearanceResponse clearance(String accountReference) {
        return accounting.get().uri("/internal/v1/account-clearances/CREDIT_CARD_ACCOUNT/{accountReference}?currencyCode=INR",
                        accountReference)
                .headers(headers -> applyHeaders(headers, null))
                .retrieve().body(ClearanceResponse.class);
    }

    @Override
    public LifecycleResponse publishClosure(AccountClosedEvent event) {
        return accounting.post().uri("/internal/v1/account-lifecycle-events")
                .headers(headers -> applyHeaders(headers, "CARD-CLOSE:" + event.accountReference()))
                .body(event).retrieve().body(LifecycleResponse.class);
    }

    private void applyHeaders(HttpHeaders headers, String idempotencyKey) {
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-Correlation-Id", correlationId());
    }

    private String correlationId() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String current = servletAttributes.getRequest().getHeader("X-Correlation-Id");
            if (current != null && !current.isBlank()) return current;
        }
        return UUID.randomUUID().toString();
    }
}
