package com.moneybags.notification.notification.integration;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Profile("mock-cif")
public class MockCifClient implements CifClient {

    private final String mockEmail;

    public MockCifClient(@Value("${moneybags.mock-cif.email}") String mockEmail) {
        this.mockEmail = mockEmail;
    }

    @Override
    public CustomerProfile getCustomer(Long cifId) {
        return new CustomerProfile(cifId, "Customer", "", mockEmail);
    }
}
