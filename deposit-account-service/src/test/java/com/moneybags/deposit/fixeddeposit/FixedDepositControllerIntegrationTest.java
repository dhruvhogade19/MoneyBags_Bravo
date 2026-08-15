package com.moneybags.deposit.fixeddeposit;

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
class FixedDepositControllerIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper;

    @Test void quoteBookReplayReadAndAccrue() throws Exception {
        String today=LocalDate.now().toString();
        mvc.perform(post("/api/deposit-accounts/fixed-deposits/quotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":\"CIF-1001\",\"productCode\":\"FD-REG-001\",\"productVersion\":1,"+
                        "\"principal\":1000,\"currency\":\"INR\",\"tenureValue\":12,\"tenureUnit\":\"MONTH\","+
                        "\"interestPayoutFrequency\":\"AT_MATURITY\",\"valueDate\":\""+today+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.annualInterestRate").value(6.75))
                .andExpect(jsonPath("$.expectedMaturityAmount").isNumber());

        String request="{\"customerIds\":[\"CIF-1001\"],\"primaryCustomerId\":\"CIF-1001\",\"productCode\":\"FD-REG-001\","+
                "\"productVersion\":1,\"principal\":1000,\"currency\":\"INR\",\"tenureValue\":12,\"tenureUnit\":\"MONTH\","+
                "\"interestPayoutFrequency\":\"AT_MATURITY\",\"fundingAccountId\":\"seed-sav-source-001\","+
                "\"payoutAccountId\":\"seed-sav-source-001\",\"servicingBranchId\":\"BR-001\",\"nominees\":[],"+
                "\"channel\":\"INTERNET_BANKING\",\"externalReference\":\"FD-IT-001\"}";
        var created=mvc.perform(post("/api/deposit-accounts/fixed-deposits").header("Idempotency-Key","fd-it-001")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        String fdId=mapper.readTree(created.getResponse().getContentAsString()).path("fixedDepositId").asText();
        mvc.perform(post("/api/deposit-accounts/fixed-deposits").header("Idempotency-Key","fd-it-001")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fixedDepositId").value(fdId));
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}",fdId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(1000));
        String eod="{\"eodRunId\":\"FD-EOD-IT\",\"businessDate\":\""+today+"\",\"commandReference\":\"FD-EOD-IT-ACCRUAL\"}";
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals").header("Idempotency-Key","fd-eod-it")
                .contentType(MediaType.APPLICATION_JSON).content(eod)).andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1));
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}/interest-accruals",fdId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessDate").value(today));
    }
}
