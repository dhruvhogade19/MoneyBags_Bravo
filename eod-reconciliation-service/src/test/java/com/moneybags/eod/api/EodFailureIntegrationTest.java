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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"eureka.client.enabled=false", "moneybags.security.enabled=false",
        "moneybags.eod.initial-business-date=2026-08-15", "moneybags.eod.stub-fail-on=DEPOSIT_READINESS"})
@AutoConfigureMockMvc
class EodFailureIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void dummyFailureBlocksRunAndRequiresExceptionResolution() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/eod/runs")
                        .header("Idempotency-Key", "eod-failure-20260815")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessDate\":\"2026-08-15\",\"startedBy\":\"test.operator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"))
                .andExpect(jsonPath("$.steps[3].status").value("FAILED"))
                .andExpect(jsonPath("$.steps[4].status").value("PENDING"))
                .andExpect(jsonPath("$.exceptions[0].status").value("OPEN"))
                .andReturn();
        JsonNode json = mapper.readTree(result.getResponse().getContentAsString());
        String runId = json.path("runId").asText();
        String exceptionId = json.path("exceptions").get(0).path("exceptionId").asText();

        mockMvc.perform(post("/api/v1/eod/runs/{runId}/resume", runId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"test.operator\",\"reason\":\"try again\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("UNRESOLVED_EOD_EXCEPTION"));
        mockMvc.perform(post("/api/v1/eod/exceptions/{exceptionId}/resolve", exceptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolution\":\"Approved temporary waiver\",\"resolvedBy\":\"supervisor\",\"waived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.exceptions[0].status").value("WAIVED"));
    }
}
