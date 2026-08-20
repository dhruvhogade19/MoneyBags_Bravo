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
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                .andExpect(jsonPath("$.paths['/api/v1/accounting/dashboard'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/account-ledgers/{accountReference}/balance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/account-ledgers/{accountReference}/entries'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/account-ledgers/{accountType}/{accountReference}/clearance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reconciliations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/accounting/eod-runs'].get").exists())
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

        mockMvc.perform(get("/api/v1/account-ledgers/{reference}/balance", destination))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ledgerBalance").value(125.0));
        mockMvc.perform(get("/api/v1/account-ledgers/{reference}/entries", destination))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].journalNumber").value(journalNumber));
        mockMvc.perform(get("/api/v1/accounting/dashboard").param("businessDate", LocalDate.now().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.journalCount").isNumber());
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
    void logicalClosureRetryReplaysAfterCallerRollbackEvenWhenItsTimestampChanges() throws Exception {
        String account = "FD-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", account);
        String closeReference = "DEPOSIT-CLOSE:" + account;
        LocalDate firstBusinessDate = LocalDate.of(2026, 8, 13);

        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeReference).header("X-Correlation-Id", "first-attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closureLifecycle(closeReference, account, firstBusinessDate,
                                OffsetDateTime.parse("2026-08-20T01:50:37+05:30"), "FD_MATURITY_PAID")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeReference).header("X-Correlation-Id", "retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closureLifecycle(closeReference, account, firstBusinessDate.plusDays(1),
                                OffsetDateTime.parse("2026-08-20T01:51:46+05:30"), "FD_MATURITY_PAID")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountingLifecycleState").value("CLOSED"))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeReference).header("X-Correlation-Id", "bad-retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closureLifecycle(closeReference, account, firstBusinessDate,
                                OffsetDateTime.parse("2026-08-20T01:52:00+05:30"), "CUSTOMER_REQUEST")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/internal/v1/account-lifecycle-events")
                        .header("Idempotency-Key", closeReference).header("X-Correlation-Id", "wrong-currency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(closureLifecycle(closeReference, account, firstBusinessDate,
                                OffsetDateTime.parse("2026-08-20T01:52:01+05:30"), "FD_MATURITY_PAID")
                                .replace("\"currencyCode\":\"INR\"", "\"currencyCode\":\"USD\"")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
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
    void depositEodAccrualAndMaturityContractClearsFixedDepositLedger() throws Exception {
        String fixedDepositAccount = "FD-EOD-" + UUID.randomUUID();
        String payoutAccount = "DEP-PAYOUT-" + UUID.randomUUID();
        String eodRunId = "EOD-FD-" + UUID.randomUUID();
        LocalDate businessDate = LocalDate.now();
        OffsetDateTime occurredAt = businessDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", fixedDepositAccount);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", payoutAccount);

        String fundingReference = "FD-FUNDING:" + fixedDepositAccount;
        String funding = """
                {"postingReference":"%s","postingType":"FUNDING",
                 "fixedDepositAccountId":"%s","productCode":"FD-12M-STANDARD",
                 "currencyCode":"INR","businessDate":"%s","occurredAt":"%s",
                 "components":[{"componentType":"PRINCIPAL","amount":10000.0000}],
                 "fundingAccountId":"%s","reasonCode":"FD_FUNDED","narration":"Fixed Deposit funding"}
                """.formatted(fundingReference, fixedDepositAccount, businessDate, occurredAt, payoutAccount);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings")
                        .header("Idempotency-Key", fundingReference)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(funding))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalDebit").value(10000.0))
                .andExpect(jsonPath("$.totalCredit").value(10000.0));

        String firstAccrualReference = "FD-ACCRUAL:" + fixedDepositAccount + ":" + businessDate.minusDays(2);
        String firstAccrual = """
                {"postingReference":"%s","postingType":"INTEREST_ACCRUAL",
                 "fixedDepositAccountId":"%s","productCode":"FD-12M-STANDARD",
                 "currencyCode":"INR","businessDate":"%s","occurredAt":"%s",
                 "components":[{"componentType":"INTEREST","amount":250.0000}],
                 "reasonCode":"EOD_ACCRUAL","narration":"FD interest accrual"}
                """.formatted(firstAccrualReference, fixedDepositAccount, businessDate, occurredAt);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings")
                        .header("Idempotency-Key", firstAccrualReference)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(firstAccrual))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.correlationId").value(eodRunId))
                .andExpect(jsonPath("$.totalDebit").value(250.0));

        String finalAccrualReference = "FD-ACCRUAL:" + fixedDepositAccount + ":" + businessDate.minusDays(1);
        String finalAccrual = """
                {"postingReference":"%s","postingType":"INTEREST_ACCRUAL",
                 "fixedDepositAccountId":"%s","productCode":"FD-12M-STANDARD",
                 "currencyCode":"INR","businessDate":"%s","occurredAt":"%s",
                 "components":[{"componentType":"INTEREST","amount":400.0000}],
                 "reasonCode":"EOD_ACCRUAL","narration":"FD final interest accrual"}
                """.formatted(finalAccrualReference, fixedDepositAccount, businessDate, occurredAt);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings")
                        .header("Idempotency-Key", finalAccrualReference)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(finalAccrual))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.correlationId").value(eodRunId))
                .andExpect(jsonPath("$.totalDebit").value(400.0));

        String maturityReference = "FD-MATURITY:" + fixedDepositAccount;
        String maturity = """
                {"postingReference":"%s","postingType":"MATURITY_PAYOUT",
                 "fixedDepositAccountId":"%s","productCode":"FD-12M-STANDARD",
                 "currencyCode":"INR","businessDate":"%s","occurredAt":"%s",
                 "components":[{"componentType":"PRINCIPAL","amount":10000.0000},
                               {"componentType":"INTEREST","amount":650.0000}],
                 "payoutAccountId":"%s","reasonCode":"FD_MATURITY_PAID",
                 "narration":"Fixed Deposit maturity payout"}
                """.formatted(maturityReference, fixedDepositAccount, businessDate, occurredAt, payoutAccount);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings")
                        .header("Idempotency-Key", maturityReference)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(maturity))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.eventType").value("FD_MATURITY_PAYOUT"))
                .andExpect(jsonPath("$.correlationId").value(eodRunId))
                .andExpect(jsonPath("$.journalNumber").isNotEmpty())
                .andExpect(jsonPath("$.totalDebit").value(10650.0))
                .andExpect(jsonPath("$.totalCredit").value(10650.0))
                .andExpect(jsonPath("$.lines.length()").value(3));

        mockMvc.perform(get("/internal/v1/account-clearances/DEPOSIT_ACCOUNT/{reference}", fixedDepositAccount)
                        .param("currencyCode", "INR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountReference").value(fixedDepositAccount))
                .andExpect(jsonPath("$.accountingCleared").value(true))
                .andExpect(jsonPath("$.blockers").isEmpty())
                .andExpect(jsonPath("$.lastPostingSequence").isNumber());
    }

    @Test
    void reconciliationReplayReturnsCurrentResolvedStateAndStillValidatesTheRequest() throws Exception {
        LocalDate date = LocalDate.of(2099, 12, 31);
        String eodRunId = "EOD-RECON-" + UUID.randomUUID();
        String key = "RECON-" + UUID.randomUUID();

        String matchingFd = "FD-MATCH-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", matchingFd);
        postFixedDepositAccrual(matchingFd, date, "10.0000", eodRunId);
        String unrelatedFd = "FD-OTHER-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", unrelatedFd);
        postFixedDepositAccrual(unrelatedFd, date, "99.0000", "OTHER-" + UUID.randomUUID());

        String aggregateEodRunId = "EOD-AGGREGATE-" + UUID.randomUUID();
        String aggregateKey = "RECON-AGGREGATE-" + UUID.randomUUID();
        String aggregateBody = """
                {"eodRunId":"%s","stepCode":"FIXED_DEPOSIT_RECONCILIATION",
                 "commandReference":"%s","businessDate":"%s",
                 "reconciledService":"DEPOSIT-ACCOUNT-SERVICE","currencyCode":"INR",
                 "expectedJournalCount":2,"expectedTotalDebit":109.0000}
                """.formatted(aggregateEodRunId, aggregateKey, date);
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", aggregateKey)
                        .header("X-Correlation-Id", aggregateEodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(aggregateBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.actualJournalCount").value(2))
                .andExpect(jsonPath("$.actualTotalDebit").value(109.0));

        String body = """
                {"eodRunId":"%s","stepCode":"FIXED_DEPOSIT_RECONCILIATION",
                 "commandReference":"%s","businessDate":"%s",
                 "reconciledService":"DEPOSIT-ACCOUNT-SERVICE","journalCorrelationId":"%s",
                 "currencyCode":"INR","expectedJournalCount":2,"expectedTotalDebit":20.0000}
                """.formatted(eodRunId, key, date, eodRunId);

        MvcResult first = mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", key).header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXCEPTION"))
                .andExpect(jsonPath("$.actualJournalCount").value(1))
                .andExpect(jsonPath("$.actualTotalDebit").value(10.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        JsonNode created = json(first);
        String runId = created.path("runId").asText();

        for (JsonNode item : created.path("items")) {
            String itemId = item.path("itemId").asText();
            String resolution = """
                    {"itemId":"%s","status":"RESOLVED",
                     "resolution":"Reviewed and resolved","actorId":"accounting-operations"}
                    """.formatted(itemId);
            mockMvc.perform(post("/api/v1/reconciliations/{runId}/resolution", runId)
                            .header("Idempotency-Key", "RESOLVE-" + itemId)
                            .contentType(MediaType.APPLICATION_JSON).content(resolution))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", key).header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(runId))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[1].status").value("RESOLVED"));

        String changedBody = body.replace("\"expectedTotalDebit\":20.0000",
                "\"expectedTotalDebit\":21.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", key).header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(changedBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void higherEpochRefreshesReconciliationAndSupersedesOldBlockers() throws Exception {
        LocalDate date = LocalDate.of(2197, 1, 10);
        String eodRunId = "EOD-REFRESH-" + UUID.randomUUID();
        String commandReference = "RECON-CONTROL-" + UUID.randomUUID();

        String firstFd = "FD-REFRESH-A-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", firstFd);
        postFixedDepositAccrual(firstFd, date, "10.0000", eodRunId);

        String firstBody = reconciliationBody(eodRunId, commandReference, date, 1, "20.0000");
        MvcResult first = mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", commandReference + ":EPOCH:1")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXCEPTION"))
                .andExpect(jsonPath("$.actualJournalCount").value(1))
                .andExpect(jsonPath("$.actualTotalDebit").value(10.0))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        String firstRunId = json(first).path("runId").asText();

        String secondFd = "FD-REFRESH-B-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", secondFd);
        postFixedDepositAccrual(secondFd, date, "10.0000", eodRunId);

        String secondBody = reconciliationBody(eodRunId, commandReference, date, 2, "20.0000");
        MvcResult refreshed = mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", commandReference + ":EPOCH:2")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.actualJournalCount").value(2))
                .andExpect(jsonPath("$.actualTotalDebit").value(20.0))
                .andExpect(jsonPath("$.items").isEmpty())
                .andReturn();
        String refreshedRunId = json(refreshed).path("runId").asText();

        mockMvc.perform(get("/api/v1/reconciliations/{runId}", firstRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[0].resolvedBy").value("SYSTEM_EOD_REFRESH"))
                .andExpect(jsonPath("$.items[1].status").value("RESOLVED"))
                .andExpect(jsonPath("$.items[1].resolvedBy").value("SYSTEM_EOD_REFRESH"));

        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", commandReference + ":LOWER-REPLAY")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(firstRunId))
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", commandReference + ":STALE-CONFLICT")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstBody.replace("\"expectedTotalDebit\":20.0000",
                                "\"expectedTotalDebit\":21.0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD_CONTROL_ATTEMPT_CONFLICT"));

        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", commandReference + ":EPOCH:2:REPLAY")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(refreshedRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"));

        String periodCommand = "{\"eodRunId\":\"" + eodRunId + "\",\"actorId\":\"EOD-SERVICE\"}";
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", date)
                        .header("Idempotency-Key", "OPEN:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk());
        String trial = "{\"businessDate\":\"" + date
                + "\",\"currencyCode\":\"INR\",\"generatedBy\":\"EOD-SERVICE\"}";
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(trial))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true));
        String paymentControl = controlReconciliationBody(eodRunId, "PAYMENTS_RECONCILIATION",
                "PAYMENTS-SERVICE", null, date, 1, 0, "0.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentControl))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void paymentAndFixedDepositControlsShareRealEodRunIdentityAndRefreshIndependently() throws Exception {
        LocalDate date = LocalDate.of(2196, 3, 15);
        String eodRunId = "EOD-MULTI-CONTROL-" + UUID.randomUUID();
        String source = "DEP-MULTI-SRC-" + UUID.randomUUID();
        String destination = "DEP-MULTI-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);

        String firstPayment = "PAY-MULTI-A-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", firstPayment).header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(firstPayment, source, destination, "30.0000", date)))
                .andExpect(status().isCreated());
        String firstFd = "FD-MULTI-A-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", firstFd);
        postFixedDepositAccrual(firstFd, date, "10.0000", eodRunId);

        String paymentEpochOne = controlReconciliationBody(eodRunId, "PAYMENTS_RECONCILIATION",
                "PAYMENTS-SERVICE", null, date, 1, 1, "30.0000");
        MvcResult paymentFirst = mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId + ":EPOCH:1")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentEpochOne))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andReturn();
        String paymentFirstRunId = json(paymentFirst).path("runId").asText();

        String fdEpochOne = controlReconciliationBody(eodRunId, "FIXED_DEPOSIT_RECONCILIATION",
                "DEPOSIT-ACCOUNT-SERVICE", eodRunId, date, 1, 1, "10.0000");
        MvcResult fdFirst = mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "FD-RECON:" + eodRunId + ":EPOCH:1")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(fdEpochOne))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andReturn();
        String fdFirstRunId = json(fdFirst).path("runId").asText();

        String secondFd = "FD-MULTI-B-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", secondFd);
        postFixedDepositAccrual(secondFd, date, "10.0000", eodRunId);
        String fdEpochTwo = controlReconciliationBody(eodRunId, "FIXED_DEPOSIT_RECONCILIATION",
                "DEPOSIT-ACCOUNT-SERVICE", eodRunId, date, 2, 2, "20.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "FD-RECON:" + eodRunId + ":EPOCH:2")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(fdEpochTwo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.actualJournalCount").value(2));

        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId + ":REPLAY")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentEpochOne))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(paymentFirstRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"));
        mockMvc.perform(get("/api/v1/reconciliations/{runId}", fdFirstRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"));
        mockMvc.perform(get("/api/v1/reconciliations").param("businessDate", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.content[1].eodRunId").value(eodRunId));

        String secondPayment = "PAY-MULTI-B-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", secondPayment).header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(secondPayment, source, destination, "5.0000", date)))
                .andExpect(status().isCreated());
        String paymentEpochTwo = controlReconciliationBody(eodRunId, "PAYMENTS_RECONCILIATION",
                "PAYMENTS-SERVICE", null, date, 2, 2, "35.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId + ":EPOCH:2")
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentEpochTwo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.actualJournalCount").value(2));
        mockMvc.perform(get("/api/v1/reconciliations").param("businessDate", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        mockMvc.perform(get("/api/v1/accounting/eod-runs/{runId}", eodRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eodRunId").value(eodRunId))
                .andExpect(jsonPath("$.reconciliationStatus").value("MATCHED"));
    }

    @Test
    void higherEpochTrialBalanceReflectsLateJournalAndPreservesOldSnapshot() throws Exception {
        LocalDate date = LocalDate.of(2198, 2, 20);
        String source = "DEP-TB-SRC-" + UUID.randomUUID();
        String destination = "DEP-TB-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);

        String firstPayment = "PAY-TB-A-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", firstPayment).header("X-Correlation-Id", firstPayment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(firstPayment, source, destination, "15.0000", date)))
                .andExpect(status().isCreated());

        String firstBody = trialBalanceBody(date, 1, "EOD-SERVICE");
        MvcResult first = mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + date + ":EPOCH:1")
                        .header("X-Correlation-Id", "TB-EPOCH-1")
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true)).andReturn();
        String firstRunId = json(first).path("runId").asText();
        BigDecimal firstTotal = json(first).path("totalDebit").decimalValue();

        String latePayment = "PAY-TB-LATE-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", latePayment).header("X-Correlation-Id", latePayment)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(latePayment, source, destination, "25.0000", date)))
                .andExpect(status().isCreated());

        String secondBody = trialBalanceBody(date, 2, "EOD-SERVICE");
        MvcResult second = mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + date + ":EPOCH:2")
                        .header("X-Correlation-Id", "TB-EPOCH-2")
                        .contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true)).andReturn();
        BigDecimal refreshedTotal = json(second).path("totalDebit").decimalValue();
        assertEquals(0, firstTotal.add(new BigDecimal("25.0000")).compareTo(refreshedTotal));

        mockMvc.perform(get("/api/v1/trial-balances/{runId}", firstRunId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebit").value(firstTotal.doubleValue()));
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + date + ":LOWER-REPLAY")
                        .header("X-Correlation-Id", "TB-LOWER-REPLAY")
                        .contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.runId").value(firstRunId));
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + date + ":STALE-CONFLICT")
                        .header("X-Correlation-Id", "TB-STALE-CONFLICT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trialBalanceBody(date, 1, "DIFFERENT-ACTOR")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EOD_CONTROL_ATTEMPT_CONFLICT"));
    }

    @Test
    void legacyDtoConstructorsDefaultExecutionEpochToOne() {
        LocalDate date = LocalDate.of(2199, 1, 1);
        var trial = new com.moneybags.accounting.api.AccountingDtos.TrialBalanceRequest(
                date, "INR", "EOD-SERVICE");
        var reconciliation = new com.moneybags.accounting.api.AccountingDtos.FinancialReconciliationRequest(
                "EOD-LEGACY", "PAYMENTS_RECONCILIATION", "LEGACY", date,
                "PAYMENTS-SERVICE", "INR", 0, BigDecimal.ZERO);
        assertEquals(1, trial.executionEpoch());
        assertEquals(1, reconciliation.executionEpoch());
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
        String eodRunId = "EOD-" + UUID.randomUUID();
        String source = "DEP-SRC-" + UUID.randomUUID(); String destination = "DEP-DST-" + UUID.randomUUID();
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", source);
        open("DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_OPENED", destination);
        String paymentId = "PAY-" + UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/payment-postings/settlements")
                        .header("Idempotency-Key", paymentId).header("X-Correlation-Id", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payment(paymentId, source, destination, "50.0000", date)))
                .andExpect(status().isCreated());
        String periodCommand = "{\"eodRunId\":\"" + eodRunId + "\",\"actorId\":\"EOD-SERVICE\"}";
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
        String paymentControl = controlReconciliationBody(eodRunId, "PAYMENTS_RECONCILIATION",
                "PAYMENTS-SERVICE", null, date, 1, 1, "50.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentControl))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        String fixedDepositControl = controlReconciliationBody(eodRunId, "FIXED_DEPOSIT_RECONCILIATION",
                "DEPOSIT-ACCOUNT-SERVICE", eodRunId, date, 1, 0, "0.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "FD-RECON:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(fixedDepositControl))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE:" + date).header("X-Correlation-Id", "eod-close")
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE-REPLAY:" + date)
                        .header("X-Correlation-Id", "eod-close-replay")
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(get("/internal/v1/accounting-periods/{date}", date))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value(date.toString()))
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void periodCloseRequiresFixedDepositControlForTheSameEodRun() throws Exception {
        LocalDate date = LocalDate.of(2195, 4, 1);
        String eodRunId = "EOD-MISSING-FD-" + UUID.randomUUID();
        String periodCommand = "{\"eodRunId\":\"" + eodRunId + "\",\"actorId\":\"EOD-SERVICE\"}";

        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", date)
                        .header("Idempotency-Key", "OPEN:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trialBalanceBody(date, 1, "EOD-SERVICE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true));

        String paymentControl = controlReconciliationBody(eodRunId, "PAYMENTS_RECONCILIATION",
                "PAYMENTS-SERVICE", null, date, 1, 0, "0.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "PAYMENT-RECON:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(paymentControl))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));

        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE-MISSING-FD:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FIXED_DEPOSIT_RECONCILIATION_REQUIRED"));

        String fixedDepositControl = controlReconciliationBody(eodRunId, "FIXED_DEPOSIT_RECONCILIATION",
                "DEPOSIT-ACCOUNT-SERVICE", eodRunId, date, 1, 0, "0.0000");
        mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                        .header("Idempotency-Key", "FD-RECON:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(fixedDepositControl))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", date)
                        .header("Idempotency-Key", "CLOSE-COMPLETE:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void openingANewPeriodRequiresEveryEarlierPeriodToBeClosed() throws Exception {
        LocalDate firstDate = LocalDate.of(9999, 12, 30);
        LocalDate laterDate = firstDate.plusDays(1);
        String eodRunId = "EOD-PERIOD-SEQUENCE-" + UUID.randomUUID();
        String periodCommand = "{\"eodRunId\":\"" + eodRunId + "\",\"actorId\":\"EOD-SERVICE\"}";

        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", firstDate)
                        .header("Idempotency-Key", "OPEN-FIRST:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", laterDate)
                        .header("Idempotency-Key", "OPEN-LATER:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EARLIER_ACCOUNTING_PERIOD_NOT_CLOSED"));

        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trialBalanceBody(firstDate, 1, "EOD-SERVICE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true));
        for (String stepCode : new String[]{"PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION"}) {
            boolean fixedDeposit = stepCode.startsWith("FIXED_DEPOSIT");
            String control = controlReconciliationBody(eodRunId, stepCode,
                    fixedDeposit ? "DEPOSIT-ACCOUNT-SERVICE" : "PAYMENTS-SERVICE",
                    fixedDeposit ? eodRunId : null, firstDate, 1, 0, "0.0000");
            mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                            .header("Idempotency-Key", stepCode + ":" + eodRunId)
                            .header("X-Correlation-Id", eodRunId)
                            .contentType(MediaType.APPLICATION_JSON).content(control))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        }
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", firstDate)
                        .header("Idempotency-Key", "CLOSE-FIRST:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(periodCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void existingOpenPeriodAndCloseStillRequireEveryEarlierPeriodToBeClosed() throws Exception {
        LocalDate earlierDate = LocalDate.of(9998, 12, 30);
        LocalDate existingDate = earlierDate.plusDays(1);
        String earlierRunId = "EOD-EARLIER-OPEN-" + UUID.randomUUID();
        String existingRunId = "EOD-EXISTING-OPEN-" + UUID.randomUUID();
        String earlierCommand = "{\"eodRunId\":\"" + earlierRunId + "\",\"actorId\":\"EOD-SERVICE\"}";
        String existingCommand = "{\"eodRunId\":\"" + existingRunId + "\",\"actorId\":\"EOD-SERVICE\"}";

        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", existingDate)
                        .header("Idempotency-Key", "OPEN-EXISTING:" + existingRunId)
                        .header("X-Correlation-Id", existingRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", earlierDate)
                        .header("Idempotency-Key", "OPEN-EARLIER:" + earlierRunId)
                        .header("X-Correlation-Id", earlierRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(earlierCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", existingDate)
                        .header("Idempotency-Key", "REOPEN-EXISTING:" + existingRunId)
                        .header("X-Correlation-Id", existingRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EARLIER_ACCOUNTING_PERIOD_NOT_CLOSED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", existingDate)
                        .header("Idempotency-Key", "CLOSE-BLOCKED:" + existingRunId)
                        .header("X-Correlation-Id", existingRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EARLIER_ACCOUNTING_PERIOD_NOT_CLOSED"));
        mockMvc.perform(get("/internal/v1/accounting-periods/{date}", existingDate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.closedAt").isEmpty())
                .andExpect(jsonPath("$.version").value(0));

        prepareZeroValueClosureControls(earlierDate, earlierRunId);
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", earlierDate)
                        .header("Idempotency-Key", "CLOSE-EARLIER:" + earlierRunId)
                        .header("X-Correlation-Id", earlierRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(earlierCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/open", existingDate)
                        .header("Idempotency-Key", "REPLAY-EXISTING:" + existingRunId)
                        .header("X-Correlation-Id", existingRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
        prepareZeroValueClosureControls(existingDate, existingRunId);
        mockMvc.perform(post("/internal/v1/accounting-periods/{date}/close", existingDate)
                        .header("Idempotency-Key", "CLOSE-EXISTING:" + existingRunId)
                        .header("X-Correlation-Id", existingRunId)
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private void prepareZeroValueClosureControls(LocalDate date, String eodRunId) throws Exception {
        mockMvc.perform(post("/internal/v1/trial-balances")
                        .header("Idempotency-Key", "TB:" + eodRunId)
                        .header("X-Correlation-Id", eodRunId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trialBalanceBody(date, 1, "EOD-SERVICE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.balanced").value(true));
        for (String stepCode : new String[]{"PAYMENTS_RECONCILIATION", "FIXED_DEPOSIT_RECONCILIATION"}) {
            boolean fixedDeposit = stepCode.startsWith("FIXED_DEPOSIT");
            String control = controlReconciliationBody(eodRunId, stepCode,
                    fixedDeposit ? "DEPOSIT-ACCOUNT-SERVICE" : "PAYMENTS-SERVICE",
                    fixedDeposit ? eodRunId : null, date, 1, 0, "0.0000");
            mockMvc.perform(post("/internal/v1/eod/reconciliation/runs")
                            .header("Idempotency-Key", stepCode + ":" + eodRunId)
                            .header("X-Correlation-Id", eodRunId)
                            .contentType(MediaType.APPLICATION_JSON).content(control))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("MATCHED"));
        }
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

    private String closureLifecycle(String eventReference, String accountReference, LocalDate businessDate,
                                    OffsetDateTime occurredAt, String reasonCode) {
        return "{\"eventReference\":\"" + eventReference + "\",\"eventType\":\"DEPOSIT_ACCOUNT_CLOSED\"," +
                "\"accountType\":\"DEPOSIT_ACCOUNT\",\"accountReference\":\"" + accountReference +
                "\",\"currencyCode\":\"INR\",\"businessDate\":\"" + businessDate +
                "\",\"occurredAt\":\"" + occurredAt + "\",\"reasonCode\":\"" + reasonCode + "\"}";
    }

    private String payment(String paymentId, String source, String destination, String amount, LocalDate date) {
        return """
                {"paymentId":"%s","paymentType":"BOOK_TRANSFER",
                 "source":{"instrumentType":"DEPOSIT_ACCOUNT","accountId":"%s"},
                 "destination":{"instrumentType":"DEPOSIT_ACCOUNT","accountId":"%s"},
                 "amount":%s,"currencyCode":"INR","occurredAt":"%s","businessDate":"%s"}
                """.formatted(paymentId, source, destination, amount, OffsetDateTime.now(), date);
    }

    private void postFixedDepositAccrual(String fd, LocalDate date, String amount, String correlationId)
            throws Exception {
        String reference = "FD:" + fd + ":ACCRUAL:" + date;
        String body = """
                {"postingReference":"%s","fixedDepositAccountId":"%s",
                 "productCode":"FD-12M-STANDARD","currencyCode":"INR","businessDate":"%s",
                 "occurredAt":"%s","components":[{"componentType":"INTEREST","amount":%s}]}
                """.formatted(reference, fd, date, OffsetDateTime.now(), amount);
        mockMvc.perform(post("/internal/v1/fixed-deposit-postings/interest-accruals")
                        .header("Idempotency-Key", reference).header("X-Correlation-Id", correlationId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correlationId").value(correlationId));
    }
    private String reconciliationBody(String eodRunId, String commandReference, LocalDate date,
                                      int executionEpoch, String expectedTotalDebit) {
        return controlReconciliationBody(eodRunId, "FIXED_DEPOSIT_RECONCILIATION",
                "DEPOSIT-ACCOUNT-SERVICE", eodRunId, date, executionEpoch, 2, expectedTotalDebit)
                .replace("\"commandReference\":\"FIXED_DEPOSIT_RECONCILIATION:" + eodRunId + "\"",
                        "\"commandReference\":\"" + commandReference + "\"");
    }
    private String controlReconciliationBody(String eodRunId, String stepCode, String reconciledService,
                                             String journalCorrelationId, LocalDate date, int executionEpoch,
                                             long expectedJournalCount, String expectedTotalDebit) {
        String correlation = journalCorrelationId == null ? ""
                : ",\"journalCorrelationId\":\"" + journalCorrelationId + "\"";
        return "{\"eodRunId\":\"" + eodRunId + "\",\"stepCode\":\"" + stepCode
                + "\",\"commandReference\":\"" + stepCode + ":" + eodRunId
                + "\",\"businessDate\":\"" + date + "\",\"reconciledService\":\"" + reconciledService
                + "\"" + correlation + ",\"currencyCode\":\"INR\",\"expectedJournalCount\":"
                + expectedJournalCount + ",\"expectedTotalDebit\":" + expectedTotalDebit
                + ",\"executionEpoch\":" + executionEpoch + "}";
    }
    private String trialBalanceBody(LocalDate date, int executionEpoch, String generatedBy) {
        return "{\"businessDate\":\"" + date + "\",\"currencyCode\":\"INR\",\"generatedBy\":\""
                + generatedBy + "\",\"executionEpoch\":" + executionEpoch + "}";
    }
    private JsonNode json(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
