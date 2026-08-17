package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class CifClient {
    private final RestClient restClient;

    public CifClient(RestClient.Builder restClientBuilder,
                     @Value("${moneybags.clients.cif.base-url:http://cif-service}") String baseUrl) {
        this.restClient = restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    @CircuitBreaker(name = "cifService")
    public DepositCreationDetails depositCreationDetails(String cifId) {
        return restClient.get().uri("/api/v1/cifs/{cifId}/deposit-creation-details", cifId)
                .retrieve().body(DepositCreationDetails.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DepositCreationDetails(String cifId, LocalDate dob, String employmentType,
                                  BigDecimal monthlyIncome, String kycStatus) {
        public LocalDate dateOfBirth() {
            return dob;
        }

        public String customerType() {
            return "BUSINESS".equalsIgnoreCase(employmentType) ? "BUSINESS" : "INDIVIDUAL";
        }

        public String customerCategory() {
            return null;
        }

        public boolean kycCompleted() {
            return "APPROVED".equalsIgnoreCase(kycStatus)
                    || "VERIFIED".equalsIgnoreCase(kycStatus);
        }
    }
}
