package com.moneybags.eod;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Component
class ClientCredentialsTokenProvider {
    private final boolean enabled;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private volatile CachedToken cached;

    ClientCredentialsTokenProvider(
            @Value("${moneybags.security.enabled:true}") boolean enabled,
            @Value("${moneybags.security.m2m.token-uri:http://localhost:8093/oauth2/token}") String tokenUri,
            @Value("${moneybags.security.m2m.client-id:eod-reconciliation-service}") String clientId,
            @Value("${moneybags.security.m2m.client-secret:${M2M_CLIENT_SECRET:}}") String clientSecret,
            @Value("${moneybags.security.m2m.scope:account:service accounting:service billing:service payment:service statements:service notification:service card:eod}") String scope) {
        this.enabled = enabled; this.tokenUri = tokenUri; this.clientId = clientId;
        this.clientSecret = clientSecret; this.scope = scope;
    }

    String token() {
        if (!enabled) return null;
        if (clientSecret.isBlank()) throw new IllegalStateException("M2M_CLIENT_SECRET is required when EOD security is enabled");
        CachedToken current = cached;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(15))) return current.value();
        synchronized (this) {
            current = cached;
            if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(15))) return current.value();
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "client_credentials"); form.add("scope", scope);
            TokenResponse response = RestClient.create().post().uri(tokenUri)
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(TokenResponse.class);
            if (response == null || response.access_token() == null)
                throw new IllegalStateException("Identity service returned no EOD access token");
            cached = new CachedToken(response.access_token(), Instant.now().plusSeconds(Math.max(60, response.expires_in())));
            return cached.value();
        }
    }

    private record TokenResponse(String access_token, long expires_in) {}
    private record CachedToken(String value, Instant expiresAt) {}
}
