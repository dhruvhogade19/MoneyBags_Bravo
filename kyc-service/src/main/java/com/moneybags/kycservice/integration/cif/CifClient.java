package com.moneybags.kycservice.integration.cif;

import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CifClient {

    private final RestClient restClient;

    public CifClient(
            @LoadBalanced RestClient.Builder builder,
            @Value("${services.cif.base-url}") String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public CifStatusUpdateResponse updateKycStatus(
            Long cifId,
            KycStatus kycStatus
    ) {

        try {

            CifStatusUpdateRequest request =
                    new CifStatusUpdateRequest(
                            cifId,
                            kycStatus
                    );

            return restClient
                    .patch()
                    .uri(
                            "/api/v1/cifs/{cifId}/kyc-status",
                            cifId
                    )
                    .body(request)
                    .retrieve()
                    .body(CifStatusUpdateResponse.class);

        } catch (Exception exception) {

            throw new ExternalServiceException(
                    "Failed to update KYC status in CIF service: "
                            + exception.getMessage()
            );
        }
    }

}