package com.moneybags.deposit.integration;

import com.moneybags.deposit.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static com.moneybags.deposit.integration.AccountingFixedDepositPostingGateway.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientAccountingFixedDepositPostingGatewayTest {

    @Test
    void postsTypedMaturityFactWithStableIntegrationHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAccountingFixedDepositPostingGateway gateway =
                new RestClientAccountingFixedDepositPostingGateway(builder, "http://accounting.test");
        LocalDate businessDate = LocalDate.of(2026, 8, 13);
        OffsetDateTime occurredAt = businessDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        FixedDepositPosting request = new FixedDepositPosting(
                "FD-MATURITY:fd-1001", "MATURITY_PAYOUT", "account-fd-1001", "FD-REG-001", "INR",
                businessDate, occurredAt, List.of(new PostingComponent("PRINCIPAL", new BigDecimal("3000.0000")),
                new PostingComponent("INTEREST", new BigDecimal("180.0000"))), null, "account-payout-1001",
                null, "FD_MATURITY_PAID", "Fixed Deposit maturity payout");

        server.expect(once(), requestTo("http://accounting.test/internal/v1/fixed-deposit-postings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "FD-MATURITY:fd-1001"))
                .andExpect(header("X-Correlation-Id", "eod-run-1001"))
                .andExpect(content().json("""
                        {"postingReference":"FD-MATURITY:fd-1001","postingType":"MATURITY_PAYOUT",
                         "fixedDepositAccountId":"account-fd-1001","productCode":"FD-REG-001",
                         "currencyCode":"INR","businessDate":"2026-08-13","occurredAt":"2026-08-13T00:00:00Z",
                         "components":[{"componentType":"PRINCIPAL","amount":3000.0000},
                                       {"componentType":"INTEREST","amount":180.0000}],
                         "payoutAccountId":"account-payout-1001","reasonCode":"FD_MATURITY_PAID",
                         "narration":"Fixed Deposit maturity payout"}
                        """))
                .andRespond(withSuccess("""
                        {"journalNumber":"JRN-20260813-00000001","status":"POSTED",
                         "totalDebit":3180.0000,"idempotentReplay":false,
                         "correlationId":"eod-run-1001"}
                        """, MediaType.APPLICATION_JSON));

        PostingResponse response = gateway.post(request, "FD-MATURITY:fd-1001", "eod-run-1001");

        assertThat(response.journalNumber()).isEqualTo("JRN-20260813-00000001");
        assertThat(response.status()).isEqualTo("POSTED");
        assertThat(response.totalDebit()).isEqualByComparingTo("3180.0000");
        assertThat(response.correlationId()).isEqualTo("eod-run-1001");
        server.verify();
    }

    @Test
    void preservesAccountingConflictDetailsAsStableDepositError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAccountingFixedDepositPostingGateway gateway =
                new RestClientAccountingFixedDepositPostingGateway(builder, "http://accounting.test");
        FixedDepositPosting request = new FixedDepositPosting(
                "FD-ACCRUAL:fd-closed:2026-08-12", "INTEREST_ACCRUAL", "account-closed",
                "FD-REG-001", "INR", LocalDate.of(2026, 8, 14),
                OffsetDateTime.parse("2026-08-14T00:00:00Z"),
                List.of(new PostingComponent("INTEREST", new BigDecimal("180.0000"))),
                null, null, null, "EOD_ACCRUAL", "legacy accrual");

        server.expect(once(), requestTo("http://accounting.test/internal/v1/fixed-deposit-postings"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                                {"code":"POSTING_TO_CLOSED_ACCOUNT",
                                 "detail":"Ordinary postings are not allowed for a closed account",
                                 "correlationId":"eod-run-conflict"}
                                """));

        assertThatThrownBy(() -> gateway.post(request, request.postingReference(), "eod-run-conflict"))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(failure.code()).isEqualTo("ACCOUNTING_POSTING_REJECTED");
                    assertThat(failure.getMessage()).contains("POSTING_TO_CLOSED_ACCOUNT")
                            .contains("eod-run-conflict")
                            .contains("Ordinary postings are not allowed");
                });
        server.verify();
    }

    @Test
    void preservesAccountingValidationFailureAsNonRetryableClientError() {
        assertMappedFailure(HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNTING_RULE_MISSING",
                "ACCOUNTING_REQUEST_REJECTED");
    }

    @Test
    void preservesAccountingAuthenticationFailureAsNonRetryableClientError() {
        assertMappedFailure(HttpStatus.UNAUTHORIZED, "INVALID_SERVICE_TOKEN",
                "ACCOUNTING_AUTHENTICATION_REJECTED");
    }

    private void assertMappedFailure(HttpStatus status, String upstreamCode, String expectedCode) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientAccountingFixedDepositPostingGateway gateway =
                new RestClientAccountingFixedDepositPostingGateway(builder, "http://accounting.test");
        FixedDepositPosting request = new FixedDepositPosting(
                "FD-ACCRUAL:fd-error:2026-08-14", "INTEREST_ACCRUAL", "account-error",
                "FD-REG-001", "INR", LocalDate.of(2026, 8, 14),
                OffsetDateTime.parse("2026-08-14T00:00:00Z"),
                List.of(new PostingComponent("INTEREST", new BigDecimal("1.0000"))),
                null, null, null, "EOD_ACCRUAL", "contract mapping test");

        server.expect(once(), requestTo("http://accounting.test/internal/v1/fixed-deposit-postings"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(status).contentType(MediaType.APPLICATION_JSON).body("""
                                {"code":"%s","detail":"Accounting rejected the posting contract",
                                 "correlationId":"eod-contract-error"}
                                """.formatted(upstreamCode)));

        assertThatThrownBy(() -> gateway.post(request, request.postingReference(), "eod-contract-error"))
                .isInstanceOfSatisfying(ApiException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(status);
                    assertThat(failure.code()).isEqualTo(expectedCode);
                    assertThat(failure.getMessage()).contains(upstreamCode).contains("eod-contract-error");
                });
        server.verify();
    }
}
