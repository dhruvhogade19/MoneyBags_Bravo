package com.moneybags.deposit.fixeddeposit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.Instant;
import java.sql.Date;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FixedDepositControllerIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;

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
                "\"interestPayoutFrequency\":\"AT_MATURITY\",\"valueDate\":\""+today+"\",\"fundingAccountId\":\"seed-sav-source-001\","+
                "\"payoutAccountId\":\"seed-sav-source-001\",\"servicingBranchId\":\"BR-001\",\"nominees\":[],"+
                "\"channel\":\"INTERNET_BANKING\",\"externalReference\":\"FD-IT-001\"}";
        var created=mvc.perform(post("/api/deposit-accounts/fixed-deposits").header("Idempotency-Key","fd-it-001")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_FUNDING")).andReturn();
        String fdId=mapper.readTree(created.getResponse().getContentAsString()).path("fixedDepositId").asText();
        fund(fdId,"fd-payment-it-001");
        mvc.perform(post("/api/deposit-accounts/fixed-deposits").header("Idempotency-Key","fd-it-001")
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.fixedDepositId").value(fdId));
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}",fdId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(1000))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.valueDate").value(today));
        String eod="{\"eodRunId\":\"FD-EOD-IT\",\"businessDate\":\""+today+"\",\"commandReference\":\"FD-EOD-IT-ACCRUAL\"}";
        mvc.perform(post("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals").header("Idempotency-Key","fd-eod-it")
                .contentType(MediaType.APPLICATION_JSON).content(eod)).andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").isNumber());
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}/interest-accruals",fdId)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].businessDate").value(today));
    }

    private void fund(String fdId,String paymentId) throws Exception {
        String reserve="{\"paymentId\":\""+paymentId+"\",\"requestorCustomerId\":1001,"+
                "\"sourceAccountId\":\"seed-sav-source-001\",\"fixedDepositId\":\""+fdId+"\","+
                "\"amount\":1000,\"currencyCode\":\"INR\",\"expiresAt\":\""+Instant.now().plusSeconds(300)+"\"}";
        var held=mvc.perform(post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/reservations")
                        .header("Idempotency-Key",paymentId+"-reserve").contentType(MediaType.APPLICATION_JSON).content(reserve))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        String reservationId=mapper.readTree(held.getResponse().getContentAsString()).path("reservationId").asText();
        String settle="{\"reservationId\":\""+reservationId+"\",\"fixedDepositId\":\""+fdId+"\","+
                "\"journalNumber\":\"JRN-"+paymentId+"\"}";
        mvc.perform(post("/internal/v1/deposit-payment-operations/fixed-deposit-funding/{paymentId}/settle",paymentId)
                        .header("Idempotency-Key",paymentId+"-settle").contentType(MediaType.APPLICATION_JSON).content(settle))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.fixedDepositStatus").value("ACTIVE"));
    }

    @Test void paymentsCanConfirmMaturityPayoutIdempotently() throws Exception {
        String today=LocalDate.now().toString(),unique="fd-payout-"+UUID.randomUUID();
        String request="{\"customerIds\":[\"CIF-1001\"],\"primaryCustomerId\":\"CIF-1001\",\"productCode\":\"FD-REG-001\","+
                "\"productVersion\":1,\"principal\":1000,\"currency\":\"INR\",\"tenureValue\":12,\"tenureUnit\":\"MONTH\","+
                "\"interestPayoutFrequency\":\"AT_MATURITY\",\"valueDate\":\""+today+"\",\"fundingAccountId\":\"seed-sav-source-001\","+
                "\"payoutAccountId\":\"seed-sav-source-001\",\"servicingBranchId\":\"BR-001\",\"nominees\":[],"+
                "\"channel\":\"INTERNET_BANKING\",\"externalReference\":\""+unique+"\"}";
        var booked=mvc.perform(post("/api/deposit-accounts/fixed-deposits").header("Idempotency-Key",unique)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated()).andReturn();
        var fd=mapper.readTree(booked.getResponse().getContentAsString());String fdId=fd.path("fixedDepositId").asText();
        fund(fdId,"payment-"+unique);jdbc.update("update FIXED_DEPOSIT set VALUE_DATE=?, MATURITY_DATE=? where FD_ID=?",
                Date.valueOf(LocalDate.now().minusDays(1)),Date.valueOf(LocalDate.now()),fdId);
        String paymentId="payout-"+unique,interest=fd.path("expectedInterest").decimalValue().toPlainString();
        String confirmation="{\"paymentId\":\""+paymentId+"\",\"journalNumber\":\"JRN-"+paymentId+"\","+
                "\"payoutAccountId\":\"seed-sav-source-001\",\"principalAmount\":1000,\"interestAmount\":"+interest+","+
                "\"netPayoutAmount\":"+fd.path("expectedMaturityAmount").decimalValue().toPlainString()+","+
                "\"currencyCode\":\"INR\",\"payoutType\":\"MATURITY\"}";
        for(int attempt=0;attempt<2;attempt++)mvc.perform(post("/internal/v1/deposit-accounts/fixed-deposits/{id}/payout-confirmations",fdId)
                        .header("Idempotency-Key",paymentId+"-confirm").contentType(MediaType.APPLICATION_JSON).content(confirmation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.paymentId").value(paymentId));
        mvc.perform(get("/api/deposit-accounts/fixed-deposits/{id}",fdId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID_OUT"));
    }
}
