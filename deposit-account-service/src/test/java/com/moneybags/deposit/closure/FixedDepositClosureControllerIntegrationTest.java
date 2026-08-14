package com.moneybags.deposit.closure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FixedDepositClosureControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

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

        String shortId = fdId.substring(0, 8);
        String accrual = eod("mat-acc-" + shortId, today);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals")
                        .header("Idempotency-Key", "eod-accrual-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(accrual))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        String maturity = eod("mat-pay-" + shortId, today);
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-maturities")
                        .header("Idempotency-Key", "eod-maturity-" + fdId)
                        .contentType(MediaType.APPLICATION_JSON).content(maturity))
                .andExpect(status().isOk()).andExpect(jsonPath("$.processed").value(1));
        mvc.perform(get("/api/deposit-accounts/{accountId}/closure-requests", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].closureType").value("FD_MATURITY"))
                .andExpect(jsonPath("$[0].status").value("CLOSED"))
                .andExpect(jsonPath("$[0].settlement.status").value("COMPLETED"));
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
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String prematureRequest() {
        return "{\"customerId\":\"CIF-1001\",\"destinationAccountId\":\"seed-sav-source-001\"," +
                "\"channel\":\"INTERNET_BANKING\",\"reasonCode\":\"CUSTOMER_REQUEST\"," +
                "\"requestedClosureDate\":\"" + LocalDate.now() + "\"}";
    }

    private String eod(String reference, LocalDate date) {
        return "{\"eodRunId\":\"" + reference + "\",\"businessDate\":\"" + date
                + "\",\"commandReference\":\"" + reference + "\"}";
    }
}
