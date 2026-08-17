package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.LocalDate;

@FeignClient(name = "cif-service", path = "/api/v1/cifs")
public interface CifClient {
    @GetMapping("/{cifId}/deposit-creation-details")
    DepositCreationDetails depositCreationDetails(@PathVariable("cifId") String cifId);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DepositCreationDetails(String cifId, LocalDate dateOfBirth, String customerType,
                                  String kycStatus, boolean active) {
        public boolean kycCompleted() {
            return "VERIFIED".equalsIgnoreCase(kycStatus);
        }
    }
}
