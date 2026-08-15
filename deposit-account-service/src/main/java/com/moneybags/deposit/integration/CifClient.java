package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class CifClient {
    private final RestClient restClient;

    public CifClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.clone().baseUrl("http://cif-service/api/v1/cifs").build();
    }

    public DepositCreationDetails depositCreationDetails(String cifId) {
        return restClient.get().uri("/{cifId}/deposit-creation-details", cifId)
                .retrieve().body(DepositCreationDetails.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepositCreationDetails(String cifId, LocalDate dateOfBirth, String customerType,
                                  String kycStatus) {
        public boolean kycCompleted() {
            return "VERIFIED".equalsIgnoreCase(kycStatus);
        }
    }
}
