package com.moneybags.creditcard.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "moneybags.credit-card.stub-upstream-clients", havingValue = "false")
public class RestClientCreditCardReferenceGateway implements CreditCardReferenceGateway {
    private final RestClient cif;
    private final RestClient product;

    public RestClientCreditCardReferenceGateway(RestClient.Builder builder, @Value("${moneybags.credit-card.cif-base-url}") String cifUrl, @Value("${moneybags.credit-card.product-base-url}") String productUrl) {
        cif = builder.baseUrl(cifUrl).build();
        product = builder.baseUrl(productUrl).build();
    }

    public CifDetails getCreditCardDetails(Long cifId) {
        return cif.get().uri("/api/v1/cifs/{id}/credit-card-details", cifId).retrieve().body(CifDetails.class);
    }

    public ProductValidation validateApplication(String code, BigDecimal limit, CifDetails c) {
        Map<String, Object> body = Map.of(
                "requestedCreditLimit", limit,
                "age", c.age(),
                "monthlyIncome", c.salary(),
                "employmentType", c.employmentType(),
                "kycCompleted", "APPROVED".equalsIgnoreCase(c.kycStatus()));
        return product.post().uri("/api/products/{code}/validate-credit-card-application", code).contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(ProductValidation.class);
    }
}
