package com.moneybags.deposit.closure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountClosureControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void quotesClosesAndReplaysCasaClosure() throws Exception {
        String accountId = openAndActivate();
        String today = LocalDate.now().toString();
        String quote = "{\"customerId\":\"CIF-CLOSE-1\",\"channel\":\"INTERNET_BANKING\"," +
                "\"requestedClosureDate\":\"" + today + "\"}";
        mvc.perform(post("/api/deposit-accounts/{id}/closure-quotes", accountId)
                        .contentType(MediaType.APPLICATION_JSON).content(quote))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.netSettlementAmount").value(0));

        String close = "{\"customerId\":\"CIF-CLOSE-1\",\"channel\":\"INTERNET_BANKING\"," +
                "\"reasonCode\":\"CUSTOMER_REQUEST\",\"reasonText\":\"No longer required\"," +
                "\"requestedClosureDate\":\"" + today + "\"}";
        var result = mvc.perform(post("/api/deposit-accounts/{id}/closure-requests", accountId)
                        .header("Idempotency-Key", "casa-close-it-1")
                        .contentType(MediaType.APPLICATION_JSON).content(close))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closureType").value("CASA_CUSTOMER_REQUEST"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.settlement.status").value("COMPLETED")).andReturn();
        String requestId = mapper.readTree(result.getResponse().getContentAsString()).path("closureRequestId").asText();

        mvc.perform(post("/api/deposit-accounts/{id}/closure-requests", accountId)
                        .header("Idempotency-Key", "casa-close-it-1")
                        .contentType(MediaType.APPLICATION_JSON).content(close))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.closureRequestId").value(requestId));
        mvc.perform(get("/api/deposit-accounts/{id}/closure-requests/{requestId}", accountId, requestId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.checks[?(@.code == 'CLOSURE_DATE')].status").value("PASSED"));
        mvc.perform(get("/api/deposit-accounts/{id}", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(get("/internal/v1/deposit-accounts/closures/{requestId}", requestId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountId").value(accountId));
    }

    @Test
    void rejectsFutureDatedExecutionAndLegacyCloseCommand() throws Exception {
        String accountId = openAndActivate();
        String future = LocalDate.now().plusDays(1).toString();
        String close = "{\"customerId\":\"CIF-CLOSE-1\",\"channel\":\"BRANCH\"," +
                "\"reasonCode\":\"CUSTOMER_REQUEST\",\"requestedClosureDate\":\"" + future + "\"}";
        mvc.perform(post("/api/deposit-accounts/{id}/closure-requests", accountId)
                        .header("Idempotency-Key", "casa-close-future-1")
                        .contentType(MediaType.APPLICATION_JSON).content(close))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionCode").value("CLOSURE_CHECK_FAILED"));
        mvc.perform(post("/api/deposit-accounts/{id}/commands/request-close", accountId)
                        .header("Idempotency-Key", "legacy-close-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"CUSTOMER_REQUEST\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USE_ACCOUNT_CLOSURE_WORKFLOW"));
    }

    private String openAndActivate() throws Exception {
        String suffix = java.util.UUID.randomUUID().toString();
        String body = "{\"customerIds\":[\"CIF-CLOSE-1\"],\"primaryCustomerId\":\"CIF-CLOSE-1\"," +
                "\"productId\":\"SAV-001\",\"productVersion\":1,\"currency\":\"INR\",\"openingAmount\":0," +
                "\"servicingBranchId\":\"BR-001\",\"operatingInstruction\":\"SINGLE\",\"nominees\":[]," +
                "\"channel\":\"BRANCH\",\"externalReference\":\"EXT-" + suffix + "\"}";
        var opened = mvc.perform(post("/api/deposit-accounts").header("Idempotency-Key", "open-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String accountId = mapper.readTree(opened.getResponse().getContentAsString()).path("accountId").asText();
        mvc.perform(post("/api/deposit-accounts/{id}/commands/activate", accountId)
                        .header("Idempotency-Key", "activate-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reasonCode\":\"OPENING_APPROVED\"}"))
                .andExpect(status().isOk());
        return accountId;
    }
}
