package com.moneybags.deposit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-level regression tests for the public and internal Deposit Account APIs.
 * The test profile uses H2 and stubbed CIF/Product Master checks; it never connects
 * to the FreeSQL database or calls another running service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepositAccountControllerIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void openingEligibilityReadingAndSearchEndpointsWork() throws Exception {
        mockMvc.perform(post("/api/v1/deposit-accounts/eligibility-check")
                        .contentType(MediaType.APPLICATION_JSON).content(eligibilityJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.decisionCode").value("ELIGIBLE"));

        String accountId = open("api-open-read-1", "CIF-API-READ", "EXT-READ-1");
        mockMvc.perform(get("/api/v1/deposit-accounts/{id}", accountId))
                .andExpect(status().isOk()).andExpect(header().string("ETag", org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.status").value("PENDING_ACTIVATION"));
        mockMvc.perform(get("/api/v1/deposit-accounts/{id}/balance", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.available").value(0));
        mockMvc.perform(get("/api/v1/deposit-accounts/{id}/status-history", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].toStatus").value("PENDING_ACTIVATION"));
        mockMvc.perform(get("/api/v1/deposit-accounts").param("customerId", "CIF-API-READ")
                        .param("status", "PENDING_ACTIVATION").param("page", "0").param("size", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].accountId").value(accountId));
    }

    @Test
    void swaggerOpenApiDefinitionLoads() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Moneybags Deposit Account API"));
    }

    @Test
    void openingIsIdempotentAndInvalidOpeningRequestsAreRejected() throws Exception {
        String first = open("api-idempotency-1", "CIF-IDEMP", "EXT-IDEMP-1");
        MvcResult replay = mockMvc.perform(post("/api/v1/deposit-accounts").header("Idempotency-Key", "api-idempotency-1")
                        .contentType(MediaType.APPLICATION_JSON).content(openJson("CIF-IDEMP", "EXT-IDEMP-1")))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json(replay).path("accountId").asText()).isEqualTo(first);

        mockMvc.perform(post("/api/v1/deposit-accounts").header("Idempotency-Key", "api-idempotency-1")
                        .contentType(MediaType.APPLICATION_JSON).content(openJson("CIF-IDEMP", "EXT-CHANGED")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mockMvc.perform(post("/api/v1/deposit-accounts").header("Idempotency-Key", "missing-primary-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(openJsonWithCustomers("CIF-OTHER", "CIF-MISSING", "EXT-INVALID")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRIMARY_HOLDER_MISSING"));
    }

    @Test
    void holderNomineeLimitAndMandateEndpointsWorkAndValidateBusinessRules() throws Exception {
        String accountId = open("api-maintenance-1", "CIF-MAINT", "EXT-MAINT-1");
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/holders", accountId).header("Idempotency-Key", "holder-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CIF-JOINT\",\"role\":\"JOINT\",\"authorizationType\":\"JOINT_HOLDER\",\"ownershipPercentage\":50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.holders.length()").value(2));
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/holders", accountId).header("Idempotency-Key", "holder-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CIF-JOINT\",\"role\":\"JOINT\",\"authorizationType\":\"JOINT_HOLDER\",\"ownershipPercentage\":50}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.holders.length()").value(2));
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/holders", accountId).header("Idempotency-Key", "holder-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"CIF-DIFFERENT\",\"role\":\"JOINT\",\"authorizationType\":\"JOINT_HOLDER\",\"ownershipPercentage\":50}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        mockMvc.perform(delete("/api/v1/deposit-accounts/{id}/holders/{customerId}", accountId, "CIF-JOINT")
                        .header("Idempotency-Key", "remove-holder-1"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/deposit-accounts/{id}/holders/{customerId}", accountId, "CIF-MAINT")
                        .header("Idempotency-Key", "remove-primary-1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRIMARY_HOLDER_REQUIRED"));

        mockMvc.perform(put("/api/v1/deposit-accounts/{id}/nominees", accountId).header("Idempotency-Key", "nominees-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Nominee One\",\"relationshipCode\":\"SPOUSE\",\"allocationPercentage\":100}]") )
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].allocationPercentage").value(100));
        mockMvc.perform(put("/api/v1/deposit-accounts/{id}/nominees", accountId).header("Idempotency-Key", "nominees-invalid-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Nominee One\",\"relationshipCode\":\"SPOUSE\",\"allocationPercentage\":60}]") )
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_NOMINEE_ALLOCATION"));

        mockMvc.perform(put("/api/v1/deposit-accounts/{id}/limits/DAILY_DEBIT", accountId).header("Idempotency-Key", "limit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limitType\":\"DAILY_DEBIT\",\"amount\":50000,\"currency\":\"INR\",\"effectiveFrom\":\"2026-08-10T00:00:00Z\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("DAILY_DEBIT"));
        mockMvc.perform(put("/api/v1/deposit-accounts/{id}/limits/DAILY_DEBIT", accountId).header("Idempotency-Key", "limit-mismatch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"limitType\":\"DAILY_CREDIT\",\"amount\":50000,\"currency\":\"INR\",\"effectiveFrom\":\"2026-08-10T00:00:00Z\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LIMIT_TYPE_MISMATCH"));

        MvcResult mandate = mockMvc.perform(post("/api/v1/deposit-accounts/{id}/mandates", accountId).header("Idempotency-Key", "mandate-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"authorizedCustomerId\":\"CIF-MANDATE\",\"mandateType\":\"OPERATE\",\"validFrom\":\"2026-08-10T00:00:00Z\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE")).andReturn();
        mockMvc.perform(delete("/api/v1/deposit-accounts/{id}/mandates/{mandateId}", accountId,
                        json(mandate).path("mandateId").asText()).header("Idempotency-Key", "revoke-mandate-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void lifecycleAndInternalEligibilityEndpointsWork() throws Exception {
        String accountId = open("api-lifecycle-1", "CIF-LIFECYCLE", "EXT-LIFE-1");
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/commands/activate", accountId).header("Idempotency-Key", "activate-1")
                        .contentType(MediaType.APPLICATION_JSON).content(commandJson("OPENING_APPROVED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/internal/deposit-accounts/{id}/eligibility", accountId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.debitAllowed").value(true))
                .andExpect(jsonPath("$.creditAllowed").value(true));
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/commands/block", accountId).header("Idempotency-Key", "block-1")
                        .header("If-Match", "\"999999\"").contentType(MediaType.APPLICATION_JSON).content(commandJson("RISK_REVIEW")))
                .andExpect(status().isPreconditionFailed()).andExpect(jsonPath("$.code").value("STALE_ACCOUNT_VERSION"));
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/commands/block", accountId).header("Idempotency-Key", "block-2")
                        .contentType(MediaType.APPLICATION_JSON).content(commandJson("RISK_REVIEW")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BLOCKED"));
        mockMvc.perform(post("/api/v1/deposit-accounts/{id}/commands/unknown", accountId).header("Idempotency-Key", "unknown-1")
                        .contentType(MediaType.APPLICATION_JSON).content(commandJson("TEST")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_COMMAND"));
    }

    private String open(String key, String customerId, String externalReference) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/deposit-accounts").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(openJson(customerId, externalReference)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountId").isNotEmpty()).andReturn();
        return json(result).path("accountId").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String eligibilityJson() {
        return "{\"customerId\":\"CIF-ELIGIBLE\",\"productId\":\"SAV-001\",\"productVersion\":1,\"currency\":\"INR\",\"openingAmount\":0}";
    }

    private String openJson(String customerId, String externalReference) {
        return openJsonWithCustomers(customerId, customerId, externalReference);
    }

    private String openJsonWithCustomers(String customerId, String primaryCustomerId, String externalReference) {
        return "{\"customerIds\":[\"" + customerId + "\"],\"primaryCustomerId\":\"" + primaryCustomerId
                + "\",\"productId\":\"SAV-001\",\"productVersion\":1,\"currency\":\"INR\",\"openingAmount\":0,\"servicingBranchId\":\"BR-001\",\"operatingInstruction\":\"SINGLE\",\"nominees\":[],\"channel\":\"BRANCH\",\"externalReference\":\"" + externalReference + "\"}";
    }

    private String commandJson(String reasonCode) {
        return "{\"reasonCode\":\"" + reasonCode + "\"}";
    }
}
