package com.moneybags.uibff.auth;

import com.moneybags.uibff.http.UpstreamExchange;
import com.moneybags.uibff.http.UpstreamResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityRegistrationClient {
    private final RestClient identity;

    public IdentityRegistrationClient(@Qualifier("identityRestClient") RestClient identity) {
        this.identity = identity;
    }

    public UpstreamResponse register(String contentType, byte[] body) {
        return UpstreamExchange.exchange(identity, HttpMethod.POST,
                "/api/v1/identity/registrations", UpstreamExchange.contentHeaders(contentType), body);
    }
}
