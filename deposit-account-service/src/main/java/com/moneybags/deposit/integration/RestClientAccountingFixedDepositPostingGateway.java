package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "moneybags.deposit.stub-upstream-clients", havingValue = "false")
public class RestClientAccountingFixedDepositPostingGateway implements AccountingFixedDepositPostingGateway {
    private final RestClient accounting;

    public RestClientAccountingFixedDepositPostingGateway(
            RestClient.Builder builder,
            @Value("${moneybags.clients.accounting.base-url}") String baseUrl) {
        this.accounting = builder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public PostingResponse post(FixedDepositPosting request, String idempotencyKey, String correlationId) {
        try {
            PostingResponse response = accounting.post().uri("/internal/v1/fixed-deposit-postings")
                    .headers(headers -> headers(headers, idempotencyKey, correlationId))
                    .body(request)
                    .retrieve()
                    .body(PostingResponse.class);
            if (response == null) throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "ACCOUNTING_POSTING_INVALID", "Accounting returned an empty Fixed Deposit posting response");
            return response;
        } catch (RestClientResponseException failure) {
            throw AccountingUpstreamErrors.response("Fixed Deposit Accounting posting", failure);
        } catch (RestClientException failure) {
            throw AccountingUpstreamErrors.unavailable("Fixed Deposit Accounting posting", failure);
        }
    }

    private void headers(HttpHeaders headers, String idempotencyKey, String correlationId) {
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-Correlation-Id", correlationId);
    }
}
