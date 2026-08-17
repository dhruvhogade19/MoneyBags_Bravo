package com.moneybags.uibff.proxy;

import com.moneybags.uibff.api.BffApiException;
import com.moneybags.uibff.http.UpstreamExchange;
import com.moneybags.uibff.http.UpstreamResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
class CifKycApprovalClient {
    private final RestClient gateway;
    private final ObjectMapper objectMapper;

    CifKycApprovalClient(@Qualifier("gatewayRestClient") RestClient gateway,
                         ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    boolean isApproved(AuthorizedSessionResolver.AuthorizedSession session,
                       String correlationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        headers.set("X-Tenant-ID", session.tenantId());
        headers.set("X-Correlation-ID", correlationId);

        UpstreamResponse response;
        try {
            response = UpstreamExchange.exchange(
                    gateway,
                    HttpMethod.GET,
                    "/api/v1/cifs/" + session.customerId(),
                    headers,
                    null);
        } catch (Exception exception) {
            throw verificationUnavailable();
        }
        if (!response.status().is2xxSuccessful()) throw verificationUnavailable();

        try {
            String status = objectMapper.readTree(
                    new String(response.body(), StandardCharsets.UTF_8))
                    .path("kycStatus")
                    .asText();
            return "APPROVED".equalsIgnoreCase(status.trim());
        } catch (RuntimeException exception) {
            throw verificationUnavailable();
        }
    }

    private static BffApiException verificationUnavailable() {
        return new BffApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Banking access is unavailable because KYC approval could not be verified");
    }
}
