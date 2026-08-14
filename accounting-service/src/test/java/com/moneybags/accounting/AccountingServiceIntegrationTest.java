package com.moneybags.accounting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountingServiceIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void generatedOpenApiContainsCorePaymentFdLifecycleAndEodContracts() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/internal/v1/payment-postings/settlements'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/fixed-deposit-postings'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/account-lifecycle-events'].post").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/trial-balances'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/journals'].get").exists())
                .andReturn();
        Path output = Path.of("target", "generated-openapi", "accounting-service.openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }

    @Test
    void postsBalancedBookTransferAndReplaysIdempotently() throws Exception {
        String source = "DEP-SRC-" + UUID.randomUUID();
        String destination = "DEP-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);
        String paymentId = "PAY-" + UUID.randomUUID();
        String body = payment(paymentId, source, destination, "125.0000", LocalDate.now());

        MvcResult first = mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId + ":accounting")
                        .header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalDebit").value(125.0))
                .andExpect(jsonPath("$.totalCredit").value(125.0))
                .andExpect(jsonPath("$.lines.length()").value(2)).andReturn();
        String journalNumber = json(first).path("journalNumber").asText();

        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId + ":accounting")
                        .header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.journalNumber").value(journalNumber));

        mockMvc.perform(get("/internal/v1/account-balances/{reference}", destination))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ledgerBalance").value(125.0));
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForAnotherPostingReference() throws Exception {
        String source = "DEP-SRC-" + UUID.randomUUID();
        String destination = "DEP-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);
        String key = "payment-key-" + UUID.randomUUID();
        String firstPayment = "PAY-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", key).header("X-Correlation-Id", firstPayment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(firstPayment, source, destination, "10.0000", LocalDate.now())))
                .andExpect(status().isCreated());

        String secondPayment = "PAY-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", key).header("X-Correlation-Id", secondPayment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(secondPayment, source, destination, "10.0000", LocalDate.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void refundDerivesOppositeEntriesFromOriginalJournal() throws Exception {
        String source = "DEP-SRC-" + UUID.randomUUID();
        String destination = "DEP-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);
        String paymentId = "PAY-" + UUID.randomUUID();
        MvcResult posted = mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId + ":accounting")
                        .header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(paymentId, source, destination, "100.0000", LocalDate.now())))
                .andExpect(status().isCreated()).andReturn();
        String original = json(posted).path("journalNumber").asText();
        String refundId = "REF-" + UUID.randomUUID();
        String refund = """
                {"refundId":"%s","paymentId":"%s","originalJournalNumber":"%s","amount":25.0000,
                 "currencyCode":"INR","occurredAt":"%s","businessDate":"%s","reason":"Customer refund"}
                """.formatted(refundId, paymentId, original, OffsetDateTime.now(), LocalDate.now());

        mockMvc.perform(post("/internal/v1/payment-postings/refunds")
                        .header("Idempotency-Key", refundId + ":accounting")
                        .header("X-Correlation-Id", refundId)
                        .contentType(MediaType.APPLICATION_JSON).content(refund))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reversesJournalNumber").value(original))
                .andExpect(jsonPath("$.totalDebit").value(25.0))
                .andExpect(jsonPath("$.totalCredit").value(25.0));
    }

    @Test
    void closureIsAtomicAndClosedAccountRejectsOrdinaryPosting() throws Exception {
        String card = "CC-" + UUID.randomUUID();
        open("CREDIT_CARD_ACCOUNT", "CREDIT_CARD_ACCOUNT_OPENED", card);
        mockMvc.perform(get("/internal/v1/account-clearances/CREDIT_CARD_ACCOUNT/{reference}", card)
                        .param("currencyCode", "INR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountingCleared").value(true));
        String closeRef = "CARD:" + card + ":CLOSE";
        String close = lifecycle(closeRef, "CREDIT_CARD_ACCOUNT_CLOSED", "CREDIT_CARD_ACCOUNT", card, null);
        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeRef).header("X-Correlation-Id", closeRef)
                        .contentType(MediaType.APPLICATION_JSON).content(close))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountingLifecycleState").value("CLOSED"));

        String billId = "BILL-" + UUID.randomUUID();
        String bill = """
                {"billId":"%s","accountId":"%s","productCode":"CARD-STANDARD",
                 "businessDate":"%s","occurredAt":"%s","currencyCode":"INR",
                 "components":[{"componentType":"INTEREST","amount":10.0000}]}
                """.formatted(billId, card, LocalDate.now(), OffsetDateTime.now());
        mockMvc.perform(post("/internal/v1/bill-postings")
                        .header("Idempotency-Key", billId).header("X-Correlation-Id", billId)
                        .contentType(MediaType.APPLICATION_JSON).content(bill))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSTING_TO_CLOSED_ACCOUNT"));
    }

    @Test
    void nonZeroCardReceivableBlocksClosure() throws Exception {
        String card = "CC-" + UUID.randomUUID();
        open("CREDIT_CARD_ACCOUNT", "CREDIT_CARD_ACCOUNT_OPENED", card);
        String billId = "BILL-" + UUID.randomUUID();
        String bill = """
                {"billId":"%s","accountId":"%s","productCode":"CARD-STANDARD",
                 "businessDate":"%s","occurredAt":"%s","currencyCode":"INR",
                 "components":[{"componentType":"INTEREST","amount":75.0000}]}
                """.formatted(billId, card, LocalDate.now(), OffsetDateTime.now());
        mockMvc.perform(post("/internal/v1/bill-postings")
                        .header("Idempotency-Key", billId).header("X-Correlation-Id", billId)
                        .contentType(MediaType.APPLICATION_JSON).content(bill))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/internal/v1/account-clearances/CREDIT_CARD_ACCOUNT/{reference}", card)
                        .param("currencyCode", "INR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountingCleared").value(false))
                .andExpect(jsonPath("$.blockers[0]").value("NON_ZERO_BALANCE"));
        String closeRef = "CARD:" + card + ":CLOSE";
        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeRef).header("X-Correlation-Id", "card-close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycle(closeRef, "CREDIT_CARD_ACCOUNT_CLOSED",
                                "CREDIT_CARD_ACCOUNT", card, null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_CLEARED"));
    }

    @Test
    void fixedDepositFundingAndAccrualUseTypedBalancedRules() throws Exception {
        String funding = "DEP-FUND-" + UUID.randomUUID(); String fd = "FD-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", funding);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", fd);
        String fundingReference = "FD:" + fd + ":FUNDING";
        String fundingBody = """
                {"postingReference":"%s","postingType":"FUNDING","fixedDepositAccountId":"%s",
                 "productCode":"FD-12M-STANDARD","currencyCode":"INR","businessDate":"%s",
                 "occurredAt":"%s","fundingAccountId":"%s",
                 "components":[{"componentType":"PRINCIPAL","amount":10000.0000}]}
                """.formatted(fundingReference, fd, LocalDate.now(), OffsetDateTime.now(), funding);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings")
                        .header("Idempotency-Key", fundingReference).header("X-Correlation-Id", "fd-funding")
                        .contentType(MediaType.APPLICATION_JSON).content(fundingBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.eventType").value("FD_FUNDING"))
                .andExpect(jsonPath("$.totalDebit").value(10000.0))
                .andExpect(jsonPath("$.totalCredit").value(10000.0));

        String accrualReference = "FD:" + fd + ":ACCRUAL";
        String accrual = """
                {"postingReference":"%s","fixedDepositAccountId":"%s","productCode":"FD-12M-STANDARD",
                 "currencyCode":"INR","businessDate":"%s","occurredAt":"%s",
                 "components":[{"componentType":"INTEREST","amount":650.0000}]}
                """.formatted(accrualReference, fd, LocalDate.now(), OffsetDateTime.now());
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings/interest-accruals")
                        .header("Idempotency-Key", accrualReference).header("X-Correlation-Id", "fd-accrual")
                        .contentType(MediaType.APPLICATION_JSON).content(accrual))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.eventType").value("FD_INTEREST_ACCRUAL"))
                .andExpect(jsonPath("$.lines[0].glCode").value("DEPOSIT_INTEREST_EXPENSE"))
                .andExpect(jsonPath("$.lines[1].glCode").value("FD_INTEREST_PAYABLE"));
    }

    @Test
    void merchantPaymentPostsCardReceivableAgainstMerchantPayable() throws Exception {
        String cardAccount = "CC-MERCHANT-" + UUID.randomUUID();
        open("CREDIT_CARD_ACCOUNT", "CREDIT_CARD_ACCOUNT_OPENED", cardAccount);
        String paymentId = "PAY-MERCHANT-" + UUID.randomUUID();
        String body = """
                {"paymentId":"%s","paymentType":"CREDIT_CARD_MERCHANT_PAYMENT",
                 "source":{"instrumentType":"CREDIT_CARD_ACCOUNT","accountId":"%s"},
                 "destination":{"instrumentType":"MERCHANT","merchantId":"MERCHANT-1"},
                 "amount":100.0000,"currencyCode":"INR","occurredAt":"%s","businessDate":"%s"}
                """.formatted(paymentId, cardAccount, OffsetDateTime.now(), LocalDate.now());
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId).header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("CREDIT_CARD_MERCHANT_PAYMENT"))
                .andExpect(jsonPath("$.totalDebit").value(100.0))
                .andExpect(jsonPath("$.totalCredit").value(100.0))
                .andExpect(jsonPath("$.lines[0].glCode").value("CREDIT_CARD_RECEIVABLE"))
                .andExpect(jsonPath("$.lines[0].subledgerReference").value(cardAccount))
                .andExpect(jsonPath("$.lines[1].glCode").value("MERCHANT_PAYABLE"))
                .andExpect(jsonPath("$.lines[1].subledgerReference").value("MERCHANT-1"))
                .andExpect(jsonPath("$.lines[1].ruleCode")
                        .value("CREDIT_CARD_MERCHANT_PAYMENT_PRINCIPAL"));
    }

    @Test
    void trialBalanceAllowsControlledPeriodClosure() throws Exception {
        LocalDate date = LocalDate.now().plusDays(10);
        String source = "DEP-SRC-" + UUID.randomUUID(); String destination = "DEP-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);
        String paymentId = "PAY-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId).header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(paymentId, source, destination, "50.0000", date)))
                .andExpect(status().isCreated());
        String periodCommand = "{\"eodRunId\":\"EOD-" + UUID.randomUUID()
                + "\",\"actorId\":\"EOD-SERVICE\"}";
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", date)
                        .header("Idempotency-Key", "OPEN:" + date).header("X-Correlation-Id", "eod-open")
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        String trial = "{\"businessDate\":\"" + date
                + "\",\"currencyCode\":\"INR\",\"generatedBy\":\"EOD-SERVICE\"}";
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + date).header("X-Correlation-Id", "eod-tb")
                        .contentType(MediaType.APPLICATION_JSON).content(trial))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE:" + date).header("X-Correlation-Id", "eod-close")
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private void open(String accountType, String eventType, String reference) throws Exception {
        String eventReference = accountType + ":" + reference + ":OPEN";
        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", eventReference).header("X-Correlation-Id", eventReference)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lifecycle(eventReference, eventType, accountType, reference, "TEST-PRODUCT")))
                .andExpect(status().isCreated());
    }

    private String lifecycle(String eventReference, String eventType, String accountType, String reference,
                             String productCode) {
        String product = productCode == null ? "" : ",\"productCode\":\"" + productCode + "\"";
        return "{\"eventReference\":\"" + eventReference + "\",\"eventType\":\"" + eventType
                + "\",\"accountType\":\"" + accountType + "\",\"accountReference\":\"" + reference
                + "\"" + product + ",\"currencyCode\":\"INR\",\"businessDate\":\"" + LocalDate.now()
                + "\",\"occurredAt\":\"" + OffsetDateTime.now() + "\"}";
    }

    private String payment(String paymentId, String source, String destination, String amount, LocalDate date) {
        return """
                {"paymentId":"%s","paymentType":"BOOK_TRANSFER",
                 "source":{"instrumentType":"DEPOSIT_ACCOUNT","accountId":"%s"},
                 "destination":{"instrumentType":"DEPOSIT_ACCOUNT","accountId":"%s"},
                 "amount":%s,"currencyCode":"INR","occurredAt":"%s","businessDate":"%s"}
                """.formatted(paymentId, source, destination, amount, OffsetDateTime.now(), date);
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
