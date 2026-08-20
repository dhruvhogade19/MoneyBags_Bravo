package com.moneybags.deposit.closure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneybags.deposit.fixeddeposit.dto.FixedDepositRequests.EodRequest;
import com.moneybags.deposit.integration.StubAccountingFixedDepositPostingGateway;
import com.moneybags.deposit.service.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FixedDepositClosureControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StubAccountingFixedDepositPostingGateway accountingPostings;

    @BeforeEach
    void clearAccountingPostings() {
        accountingPostings.clear();
    }

    @Test
    void quotesExecutesAndReadsPrematureClosure() throws Exception {
        JsonNode fd = book("premature");
        String fdId = fd.path("fixedDepositId").asText();
        jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=? where FD_ID=?",
                Date.valueOf(LocalDate.now().minusDays(30)), fdId);

        String request = prematureRequest();
        mvc.perform(post("/api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-quotes", fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.completedHoldingDays").value(30))
                .andExpect(jsonPath("$.finalAnnualRate").value(5.75));

        var closed = mvc.perform(post("/api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-requests", fdId)
                        .header("Idempotency-Key", "fd-pc-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closureType").value("FD_PREMATURE"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.settlement.status").value("COMPLETED")).andReturn();
        String closureId = mapper.readTree(closed.getResponse().getContentAsString()).path("closureRequestId").asText();
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{fdId}/premature-closure-requests/{requestId}", fdId, closureId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.closureRequestId").value(closureId));
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{fdId}", fdId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED_PREMATURE"));
    }

    @Test
    void maturityEodCreatesCompletedClosureRecord() throws Exception {
        JsonNode fd = book("maturity");
        String fdId = fd.path("fixedDepositId").asText();
        String accountId = fd.path("accountId").asText();
        LocalDate today = LocalDate.now();
        jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=?, MATURITY_DATE=?, LAST_ACCRUAL_DATE=null where FD_ID=?",
                Date.valueOf(today.minusDays(1)), Date.valueOf(today), fdId);

        String eodRunId = UUID.randomUUID().toString();
        String accrualReference = "EOD:" + eodRunId + ":FIXED_DEPOSIT_ACCRUALS";
        String accrual = eod(eodRunId, accrualReference, today);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                .header("Idempotency-Key", "eod-accrual-" + fdId)
                .contentType(MediaType.APPLICATION_JSON).content(accrual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        assertThat(jdbc.queryForObject("select SOURCE_REFERENCE from FD_INTEREST_ACCRUAL where FD_ID=?",
                String.class, fdId)).isEqualTo("FD-ACCRUAL:" + fdId + ":" + today.minusDays(1))
                .hasSizeLessThanOrEqualTo(100);
        String accrualJournal = jdbc.queryForObject(
                "select ACCOUNTING_JOURNAL_NUMBER from FD_INTEREST_ACCRUAL where FD_ID=?", String.class, fdId);
        assertThat(accrualJournal).startsWith("JRN-STUB-").hasSizeLessThanOrEqualTo(100);
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_POSTING_STATUS from FD_INTEREST_ACCRUAL where FD_ID=?", String.class, fdId))
                .isEqualTo("POSTED");
        BigDecimal expectedInterest = jdbc.queryForObject(
                "select EXPECTED_INTEREST from FIXED_DEPOSIT where FD_ID=?", BigDecimal.class, fdId);
        BigDecimal cumulativeInterest = jdbc.queryForObject(
                "select CUMULATIVE_INTEREST from FD_INTEREST_ACCRUAL where FD_ID=?", BigDecimal.class, fdId);
        assertThat(cumulativeInterest).isEqualByComparingTo(expectedInterest);
        var accrualPosting = accountingPostings.invocations().getFirst();
        assertThat(accrualPosting.request().postingReference())
                .isEqualTo("FD-ACCRUAL:" + fdId + ":" + today.minusDays(1));
        assertThat(accrualPosting.request().postingType()).isEqualTo("INTEREST_ACCRUAL");
        assertThat(accrualPosting.request().fixedDepositAccountId()).isEqualTo(accountId);
        assertThat(accrualPosting.request().businessDate()).isEqualTo(today);
        assertThat(accrualPosting.request().occurredAt()).isEqualTo(today.atStartOfDay().atOffset(ZoneOffset.UTC));
        assertThat(accrualPosting.request().components()).singleElement().satisfies(component -> {
            assertThat(component.componentType()).isEqualTo("INTEREST");
            assertThat(component.amount()).isEqualByComparingTo(expectedInterest);
        });
        assertThat(accrualPosting.idempotencyKey()).isEqualTo(accrualPosting.request().postingReference());
        assertThat(accrualPosting.correlationId()).isEqualTo(eodRunId);

        String maturityReference = "EOD:" + eodRunId + ":FIXED_DEPOSIT_MATURITIES";
        String maturity = eod(eodRunId, maturityReference, today);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-maturities")
                .header("Idempotency-Key", "eod-maturity-" + fdId)
                .contentType(MediaType.APPLICATION_JSON).content(maturity))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        String paymentId = jdbc.queryForObject("select PAYMENT_ID from FUND_RESERVATION " +
                        "where SOURCE_ACCOUNT_ID=? and OPERATION_TYPE='FIXED_DEPOSIT_MATURITY_PAYOUT'",
                String.class, accountId);
        assertThat(paymentId).isEqualTo("FD-MATURITY:" + fdId).hasSizeLessThanOrEqualTo(64);
        assertThat(jdbc.queryForList("select PAYMENT_ID from DEPOSIT_ACCOUNT_TRANSACTION where PAYMENT_ID=?",
                String.class, paymentId)).hasSize(2).allSatisfy(value -> assertThat(value).hasSizeLessThanOrEqualTo(64));
        String payoutJournal = jdbc.queryForObject(
                "select ACCOUNTING_JOURNAL_NUMBER from FD_PAYOUT where FD_ID=?", String.class, fdId);
        assertThat(payoutJournal).startsWith("JRN-STUB-").hasSizeLessThanOrEqualTo(100);
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_POSTING_STATUS from FD_PAYOUT where FD_ID=?", String.class, fdId))
                .isEqualTo("POSTED");
        var maturityPosting = accountingPostings.invocations().get(1);
        assertThat(maturityPosting.request().postingReference()).isEqualTo("FD-MATURITY:" + fdId);
        assertThat(maturityPosting.request().postingType()).isEqualTo("MATURITY_PAYOUT");
        assertThat(maturityPosting.request().fixedDepositAccountId()).isEqualTo(accountId);
        assertThat(maturityPosting.request().payoutAccountId()).isEqualTo("seed-sav-source-001");
        assertThat(maturityPosting.request().businessDate()).isEqualTo(today);
        assertThat(maturityPosting.request().occurredAt()).isEqualTo(today.atStartOfDay().atOffset(ZoneOffset.UTC));
        BigDecimal maturityInterest = maturityPosting.request().components().stream()
                .filter(component -> component.componentType().equals("INTEREST"))
                .map(component -> component.amount()).findFirst().orElseThrow();
        assertThat(maturityInterest).isEqualByComparingTo(expectedInterest);
        assertThat(accrualPosting.request().components().getFirst().amount().subtract(maturityInterest))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(maturityPosting.idempotencyKey()).isEqualTo(maturityPosting.request().postingReference());
        assertThat(maturityPosting.correlationId()).isEqualTo(eodRunId);

        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-maturities")
                        .header("Idempotency-Key", "eod-maturity-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(maturity))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        assertThat(accountingPostings.invocations()).hasSize(2);
        mvc.perform(get("/api/deposit-accounts/{accountId}/closure-requests", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].closureType").value("FD_MATURITY"))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].settlement.status").value("COMPLETED"));
    }

    @Test
    void prePatchAccrualStateAndCachedResponseAreRecoveredThroughStablePosting() throws Exception {
        JsonNode fd = book("ar");
        String fdId = fd.path("fixedDepositId").asText();
        LocalDate today = LocalDate.now();
        LocalDate effectiveDate = today.minusDays(1);
        jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=?, MATURITY_DATE=?, LAST_ACCRUAL_DATE=null where FD_ID=?",
                Date.valueOf(effectiveDate), Date.valueOf(today), fdId);

        String initialRunId = UUID.randomUUID().toString();
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "initial-accrual-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eod(initialRunId, "EOD:" + initialRunId + ":FIXED_DEPOSIT_ACCRUALS", today)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));

        String legacyReference = "LEGACY-ACCRUAL:" + fdId;
        jdbc.update("update FD_INTEREST_ACCRUAL set ACCOUNTING_JOURNAL_NUMBER=null, " +
                        "ACCOUNTING_POSTING_STATUS=null, STATUS='CALCULATED', SOURCE_REFERENCE=? where FD_ID=?",
                legacyReference, fdId);
        accountingPostings.clear();

        String recoveryRunId = UUID.randomUUID().toString();
        String recoveryKey = "recover-accrual-" + fdId;
        String recoveryRequest = eod(recoveryRunId,
                "EOD:" + recoveryRunId + ":FIXED_DEPOSIT_ACCRUALS", today);
        cacheLegacyEodResult("FD_EOD_ACCRUAL", recoveryKey, recoveryRequest);

        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", recoveryKey)
                        .contentType(MediaType.APPLICATION_JSON).content(recoveryRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failures").isEmpty());

        assertThat(accountingPostings.invocations()).singleElement().satisfies(invocation -> {
            assertThat(invocation.request().postingReference())
                    .isEqualTo("FD-ACCRUAL:" + fdId + ":" + effectiveDate);
            assertThat(invocation.request().postingType()).isEqualTo("INTEREST_ACCRUAL");
            assertThat(invocation.request().businessDate()).isEqualTo(today);
            assertThat(invocation.correlationId()).isEqualTo(recoveryRunId);
        });
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_POSTING_STATUS from FD_INTEREST_ACCRUAL where FD_ID=?", String.class, fdId))
                .isEqualTo("POSTED");
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_JOURNAL_NUMBER from FD_INTEREST_ACCRUAL where FD_ID=?", String.class, fdId))
                .startsWith("JRN-STUB-");
        assertThat(jdbc.queryForObject(
                "select SOURCE_REFERENCE from FD_INTEREST_ACCRUAL where FD_ID=?", String.class, fdId))
                .isEqualTo(legacyReference);
        assertThat(jdbc.queryForObject("select count(*) from IDEMPOTENCY_RECORD where IDEMPOTENCY_SCOPE=? " +
                        "and KEY_HASH=?", Integer.class, "FD_EOD_ACCRUAL_ACCOUNTING_V3", Hashing.sha256(recoveryKey)))
                .isEqualTo(1);
        // Keep this recovery-only fixture out of later maturity sweeps in the shared test context.
        jdbc.update("update FIXED_DEPOSIT set STATUS='FUNDING_FAILED' where FD_ID=?", fdId);
    }

    @Test
    void postedMetricsBelongOnlyToTheJournalOwningEodCorrelation() throws Exception {
        JsonNode fd = book("correlation");
        String fdId = fd.path("fixedDepositId").asText();
        LocalDate today = LocalDate.now();
        LocalDate effectiveDate = today.minusDays(1);
        jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=?, MATURITY_DATE=?, LAST_ACCRUAL_DATE=null where FD_ID=?",
                Date.valueOf(effectiveDate), Date.valueOf(today.plusYears(1)), fdId);

        String owningRunId = UUID.randomUUID().toString();
        String owningRequest = eod(owningRunId,
                "EOD:" + owningRunId + ":FIXED_DEPOSIT_ACCRUALS", effectiveDate);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "correlation-initial-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(owningRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postedJournalCount").value(1));

        jdbc.update("update FD_INTEREST_ACCRUAL set ACCOUNTING_JOURNAL_NUMBER=null, " +
                "ACCOUNTING_POSTING_STATUS=null, STATUS='CALCULATED' where FD_ID=?", fdId);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "correlation-same-run-recovery-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(owningRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.postedJournalCount").value(1));

        jdbc.update("update FD_INTEREST_ACCRUAL set ACCOUNTING_JOURNAL_NUMBER=null, " +
                "ACCOUNTING_POSTING_STATUS=null, STATUS='CALCULATED' where FD_ID=?", fdId);
        String laterRunId = UUID.randomUUID().toString();
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "correlation-later-run-recovery-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eod(laterRunId, "EOD:" + laterRunId + ":FIXED_DEPOSIT_ACCRUALS", effectiveDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.totalAmount").isNumber())
                .andExpect(jsonPath("$.postedJournalCount").value(0))
                .andExpect(jsonPath("$.postedDebitTotal").value(0));

        jdbc.update("update FIXED_DEPOSIT set STATUS='FUNDING_FAILED' where FD_ID=?", fdId);
    }

    @Test
    void closedLegacyAccrualAndPayoutAreAuditedWithoutFabricatingJournals() throws Exception {
        JsonNode fd = book("pr");
        String fdId = fd.path("fixedDepositId").asText();
        String accountId = fd.path("accountId").asText();
        LocalDate today = LocalDate.now();
        jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=?, MATURITY_DATE=?, LAST_ACCRUAL_DATE=null where FD_ID=?",
                Date.valueOf(today.minusDays(1)), Date.valueOf(today), fdId);

        String initialRunId = UUID.randomUUID().toString();
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "initial-payout-accrual-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eod(initialRunId, "EOD:" + initialRunId + ":FIXED_DEPOSIT_ACCRUALS", today)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-maturities")
                        .header("Idempotency-Key", "initial-payout-maturity-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eod(initialRunId, "EOD:" + initialRunId + ":FIXED_DEPOSIT_MATURITIES", today)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));

        String stableReference = "FD-MATURITY:" + fdId;
        int reservationCount = jdbc.queryForObject("select count(*) from FUND_RESERVATION " +
                        "where PAYMENT_ID=? and OPERATION_TYPE='FIXED_DEPOSIT_MATURITY_PAYOUT'",
                Integer.class, stableReference);
        int transactionCount = jdbc.queryForObject(
                "select count(*) from DEPOSIT_ACCOUNT_TRANSACTION where PAYMENT_ID=?", Integer.class, stableReference);
        jdbc.update("update FD_INTEREST_ACCRUAL set ACCOUNTING_JOURNAL_NUMBER=null, " +
                "ACCOUNTING_POSTING_STATUS=null where FD_ID=?", fdId);
        String accrualRecoveryRunId = UUID.randomUUID().toString();
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "legacy-closed-accrual-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eod(accrualRecoveryRunId,
                                "EOD:" + accrualRecoveryRunId + ":FIXED_DEPOSIT_ACCRUALS", today)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.failures").isEmpty())
                .andExpect(jsonPath("$.postedJournalCount").value(0))
                .andExpect(jsonPath("$.postedDebitTotal").value(0));
        assertThat(jdbc.queryForObject("select ACCOUNTING_POSTING_STATUS from FD_INTEREST_ACCRUAL " +
                "where FD_ID=?", String.class, fdId)).isEqualTo("LEGACY_ACCEPTED");

        String legacyReference = "LEGACY-MATURITY:" + fdId;
        jdbc.update("update FD_PAYOUT set ACCOUNTING_JOURNAL_NUMBER=null, ACCOUNTING_POSTING_STATUS=null, " +
                "SOURCE_REFERENCE=? where FD_ID=?", legacyReference, fdId);
        accountingPostings.clear();

        String recoveryRunId = UUID.randomUUID().toString();
        String recoveryKey = "recover-payout-" + fdId;
        String recoveryRequest = eod(recoveryRunId,
                "EOD:" + recoveryRunId + ":FIXED_DEPOSIT_MATURITIES", today);
        cacheLegacyEodResult("FD_EOD_MATURITY", recoveryKey, recoveryRequest);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-maturities")
                        .header("Idempotency-Key", recoveryKey)
                        .contentType(MediaType.APPLICATION_JSON).content(recoveryRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.failures").isEmpty())
                .andExpect(jsonPath("$.postedJournalCount").value(0))
                .andExpect(jsonPath("$.postedDebitTotal").value(0));

        assertThat(accountingPostings.invocations()).isEmpty();
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_POSTING_STATUS from FD_PAYOUT where FD_ID=?", String.class, fdId))
                .isEqualTo("LEGACY_ACCEPTED");
        assertThat(jdbc.queryForObject(
                "select ACCOUNTING_JOURNAL_NUMBER from FD_PAYOUT where FD_ID=?", String.class, fdId)).isNull();
        assertThat(jdbc.queryForObject(
                "select SOURCE_REFERENCE from FD_PAYOUT where FD_ID=?", String.class, fdId))
                .isEqualTo(legacyReference);
        assertThat(jdbc.queryForObject("select count(*) from FUND_RESERVATION " +
                        "where PAYMENT_ID=? and OPERATION_TYPE='FIXED_DEPOSIT_MATURITY_PAYOUT'",
                Integer.class, stableReference)).isEqualTo(reservationCount);
        assertThat(jdbc.queryForObject(
                "select count(*) from DEPOSIT_ACCOUNT_TRANSACTION where PAYMENT_ID=?",
                Integer.class, stableReference)).isEqualTo(transactionCount);
        assertThat(jdbc.queryForObject(
                "select count(*) from FD_PAYOUT where FD_ID=?", Integer.class, fdId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from AUDIT_LOG where AGGREGATE_ID=? and " +
                "ACTION in ('ACCEPT_LEGACY_FD_ACCRUAL_ACCOUNTING','ACCEPT_LEGACY_FD_PAYOUT_ACCOUNTING')",
                Integer.class, fdId)).isEqualTo(2);
    }

    private JsonNode book(String scenario) throws Exception {
        String unique = scenario + "-" + UUID.randomUUID();
        String request = "{\"customerIds\":[\"CIF-1001\"],\"primaryCustomerId\":\"CIF-1001\"," +
                "\"productCode\":\"FD-REG-001\",\"productVersion\":1,\"principal\":1000,\"currency\":\"INR\"," +
                "\"tenureValue\":12,\"tenureUnit\":\"MONTH\",\"interestPayoutFrequency\":\"AT_MATURITY\"," +
                "\"fundingAccountId\":\"seed-sav-source-001\",\"payoutAccountId\":\"seed-sav-source-001\"," +
                "\"servicingBranchId\":\"BR-001\",\"nominees\":[],\"channel\":\"INTERNET_BANKING\"," +
                "\"externalReference\":\"" + unique + "\"}";
        var result = mvc.perform(post("/api/deposit-accounts/fixed-deposits")
                        .header("Idempotency-Key", unique).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_FUNDING")).andReturn();
        JsonNode fd=mapper.readTree(result.getResponse().getContentAsString());
        fund(fd.path("fixedDepositId").asText(),"payment-"+unique);
        return mapper.readTree(mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}",fd.path("fixedDepositId").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());
    }

    private void fund(String fdId,String paymentId) throws Exception {
        String reserve="{\"paymentId\":\""+paymentId+"\",\"requestorCustomerId\":1001,"+
                "\"sourceAccountId\":\"seed-sav-source-001\",\"fixedDepositId\":\""+fdId+"\","+
                "\"amount\":1000,\"currencyCode\":\"INR\",\"expiresAt\":\""+Instant.now().plusSeconds(300)+"\"}";
        var held=mvc.perform(post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/reservations")
                        .header("Idempotency-Key",paymentId+"-reserve").contentType(MediaType.APPLICATION_JSON).content(reserve))
                .andExpect(status().isCreated()).andReturn();
        String reservationId=mapper.readTree(held.getResponse().getContentAsString()).path("reservationId").asText();
        String settle="{\"reservationId\":\""+reservationId+"\",\"fixedDepositId\":\""+fdId+"\","+
                "\"journalNumber\":\"JRN-"+paymentId+"\"}";
        mvc.perform(post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/{paymentId}/settle",paymentId)
                        .header("Idempotency-Key",paymentId+"-settle").contentType(MediaType.APPLICATION_JSON).content(settle))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fixedDepositStatus").value("ACTIVE"));
    }

    private String prematureRequest() {
        return "{\"customerId\":\"CIF-1001\",\"destinationAccountId\":\"seed-sav-source-001\"," +
                "\"channel\":\"INTERNET_BANKING\",\"reasonCode\":\"CUSTOMER_REQUEST\"," +
                "\"requestedClosureDate\":\"" + LocalDate.now() + "\"}";
    }

    private String eod(String runId, String commandReference, LocalDate date) {
        return "{\"eodRunId\":\"" + runId + "\",\"businessDate\":\"" + date
                + "\",\"commandReference\":\"" + commandReference + "\"}";
    }

    private void cacheLegacyEodResult(String scope, String key, String requestBody) throws Exception {
        EodRequest request = mapper.readValue(requestBody, EodRequest.class);
        String requestHash = Hashing.sha256(mapper.writeValueAsString(request));
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("insert into IDEMPOTENCY_RECORD (RECORD_ID, IDEMPOTENCY_SCOPE, KEY_HASH, REQUEST_HASH, " +
                        "PROCESSING_STATUS, HTTP_STATUS, RESPONSE_BODY, CREATED_AT, EXPIRES_AT) " +
                        "values (?, ?, ?, ?, 'COMPLETED', 200, ?, ?, ?)",
                UUID.randomUUID().toString(), scope, Hashing.sha256(key), requestHash,
                "{\"eodRunId\":\"legacy\",\"businessDate\":\"" + request.businessDate() +
                        "\",\"commandReference\":\"legacy\",\"processed\":0,\"skipped\":1," +
                        "\"totalAmount\":0.0000,\"failures\":[]}",
                now, now.plusHours(24));
    }
}
