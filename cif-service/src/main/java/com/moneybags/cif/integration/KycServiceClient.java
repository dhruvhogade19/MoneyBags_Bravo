package com.moneybags.cif.integration;

import com.moneybags.cif.dto.request.KycVerificationRequest;
import com.moneybags.cif.exception.KycServiceUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KycServiceClient {

    private final RestClient restClient;

    public KycServiceClient(
            @Qualifier("loadBalancedRestClientBuilder")
            RestClient.Builder restClientBuilder
    ) {
        this.restClient = restClientBuilder
                .baseUrl("http://kyc-service")
                .build();
    }

    public void initiateKycVerification(
            KycVerificationRequest request
    ) {

        try {

            restClient.post()
                    .uri("/api/v1/kycs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (HttpClientErrorException exception) {

            throw new IllegalStateException(
                    "KYC rejected request. Status: "
                            + exception.getStatusCode()
                            + ", response: "
                            + exception.getResponseBodyAsString(),
                    exception
            );

        } catch (HttpServerErrorException exception) {

            throw new KycServiceUnavailableException(
                    "KYC service returned server error: "
                            + exception.getStatusCode(),
                    exception
            );

        } catch (RestClientException | IllegalStateException exception) {

            throw new KycServiceUnavailableException(
                    "Unable to communicate with KYC service",
                    exception
            );
        }
    }
}