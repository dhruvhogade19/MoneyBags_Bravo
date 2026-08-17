package com.moneybags.deposit.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "product-master-service", path = "/api/products")
public interface ProductMasterClient {
    @GetMapping("/{productCode}")
    ProductResult getProduct(@PathVariable("productCode") String productCode);

    @PostMapping("/{productCode}/validate-account-opening")
    AccountOpeningValidation validateAccountOpening(@PathVariable("productCode") String productCode,
                                                     @RequestBody AccountOpeningValidationRequest request);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductResult(String productCode, Long version, String productName, String currencyCode,
                         String accountType, String status) {
        public boolean active() { return "ACTIVE".equalsIgnoreCase(status); }
        public boolean supportedDepositType() {
            return "SAVINGS".equalsIgnoreCase(accountType) || "CURRENT".equalsIgnoreCase(accountType);
        }
    }

    record AccountOpeningValidationRequest(BigDecimal openingAmount, int age, String customerType,
                                           boolean kycCompleted) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AccountOpeningValidation(boolean eligible) {}
}
