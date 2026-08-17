package com.moneybags.cif.integration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityServiceClient {
    private final RestClient client;

    public IdentityServiceClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
        this.client = builder.clone().baseUrl("http://identity-access-service").build();
    }

    public void linkCustomer(String userId, Long cifId, String tenantId) {
        client.put().uri("/internal/v1/identity/users/{userId}/customer-link", userId)
                .body(new CustomerLinkRequest(String.valueOf(cifId), tenantId))
                .retrieve().toBodilessEntity();
    }

    private record CustomerLinkRequest(String customerId, String tenantId) {}
}
