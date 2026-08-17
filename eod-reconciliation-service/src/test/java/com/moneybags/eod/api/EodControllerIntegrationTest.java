package com.moneybags.eod.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "moneybags.security.enabled=false",
        "moneybags.eod.initial-business-date=2026-08-13"})
@AutoConfigureMockMvc
class EodControllerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void completeRunIsIdempotentAndAllowsNextBusinessDate() throws Exception {
        mockMvc.perform(get("/api/v1/business-date"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").value("2026-08-13"))
                .andExpect(jsonPath("$.status").value("OPEN"));

        String request = "{\"businessDate\":\"2026-08-13\",\"startedBy\":\"test.operator\"}";
        MvcResult first = mockMvc.perform(post("/api/v1/eod/runs")
                        .header("Idempotency-Key", "eod-test-20260813")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.steps.length()").value(15))
                .andExpect(jsonPath("$.steps[0].stepCode").value("PAYMENTS_CUTOFF"))
                .andExpect(jsonPath("$.steps[5].stepCode").value("FD_INTEREST_ACCRUAL"))
                .andExpect(jsonPath("$.steps[6].stepCode").value("FD_MATURITY_PROCESSING"))
                .andExpect(jsonPath("$.steps[7].stepCode").value("FD_ACCOUNTING_RECONCILIATION"))
                .andExpect(jsonPath("$.steps[8].stepCode").value("FD_READINESS_CHECK"))
                .andExpect(jsonPath("$.steps[3].output.ready").value(true))
                .andExpect(jsonPath("$.steps[3].output.blockers.length()").value(0))
                .andExpect(jsonPath("$.steps[4].commandReference").value("DEP-ACCRUAL-20260813-V1"))
                .andExpect(jsonPath("$.steps[4].output.processedCount").value(1250))
                .andExpect(jsonPath("$.steps[4].output.failedCount").value(0))
                .andExpect(jsonPath("$.steps[5].path").value("/internal/v1/deposit-accounts/eod/fixed-deposit-accruals"))
                .andExpect(jsonPath("$.steps[5].commandReference").value("FD-ACCRUAL-20260813-V1"))
                .andExpect(jsonPath("$.steps[5].output.eodRunId").isNotEmpty())
                .andExpect(jsonPath("$.steps[5].output.businessDate").value("2026-08-13"))
                .andExpect(jsonPath("$.steps[5].output.processed").value(750))
                .andExpect(jsonPath("$.steps[5].output.skipped").value(4))
                .andExpect(jsonPath("$.steps[5].output.failures.length()").value(0))
                .andExpect(jsonPath("$.steps[6].commandReference").value("FD-MATURITY-20260813-V1"))
                .andExpect(jsonPath("$.steps[8].output.pendingFunding").value(0))
                .andExpect(jsonPath("$.steps[8].output.pendingPayouts").value(0))
                .andExpect(jsonPath("$.steps[14].stepCode").value("ACCOUNTING_PERIOD_CLOSE"))
                .andExpect(jsonPath("$.steps[0].output.dummy").value(true))
                .andExpect(jsonPath("$.exceptions.length()").value(0))
                .andReturn();
        JsonNode firstJson = mapper.readTree(first.getResponse().getContentAsString());
        String runId = firstJson.path("runId").asText();

        mockMvc.perform(post("/api/v1/eod/runs")
                        .header("Idempotency-Key", "eod-test-20260813")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value(runId));
        mockMvc.perform(post("/api/v1/eod/runs")
                        .header("Idempotency-Key", "eod-test-20260813")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-08-13\",\"startedBy\":\"someone.else\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(get("/api/v1/eod/runs/{runId}", runId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        MvcResult next = mockMvc.perform(post("/api/v1/business-date/open-next")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-08-14\",\"openedBy\":\"test.operator\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.businessDate").value("2026-08-14")).andReturn();
        assertThat(next.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
    }
}
