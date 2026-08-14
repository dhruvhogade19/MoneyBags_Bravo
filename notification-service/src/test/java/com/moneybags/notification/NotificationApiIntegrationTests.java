package com.moneybags.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mock-cif"})
class NotificationApiIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsFailedNotificationOnceAndReplaysWithoutDuplicateDelivery() throws Exception {
        String idempotencyKey = "test-payment-" + UUID.randomUUID();
        String requestBody = paymentRequest("1500.00");

        mockMvc.perform(post("/internal/v1/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-ID", "integration-test-payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-ID", "integration-test-payment"))
                .andExpect(jsonPath("$.cifId").value(101))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.recipientEmail").doesNotExist());

        mockMvc.perform(post("/internal/v1/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        Integer notificationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE idempotency_key = ?", Integer.class, idempotencyKey);
        Integer attemptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM delivery_attempt da JOIN notification n ON n.notification_id = da.notification_id "
                        + "WHERE n.idempotency_key = ?", Integer.class, idempotencyKey);
        assertThat(notificationCount).isEqualTo(1);
        assertThat(attemptCount).isEqualTo(1);
    }

    @Test
    void rejectsChangedContentForTheSameIdempotencyKey() throws Exception {
        String idempotencyKey = "test-conflict-" + UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("1500.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/notifications")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest("1600.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returnsEmptyHistoryForCustomerWithNoNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .queryParam("cifId", "999999")
                        .queryParam("page", "0")
                        .queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void transformsRejectedKycStatusIntoRejectedKycNotification() throws Exception {
        mockMvc.perform(post("/internal/v1/notifications/kyc-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cifId": 102,
                                  "kycStatus": "REJECTED",
                                  "rejectionReason": "Document image is unclear"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationType").value("KYC_REJECTED"))
                .andExpect(jsonPath("$.emailBody").value(org.hamcrest.Matchers.containsString(
                        "Document image is unclear")));
    }

    private String paymentRequest(String amount) {
        return """
                {
                  "cifId": 101,
                  "notificationType": "PAYMENT_SUCCESS",
                  "sourceReference": "PAY-10045",
                  "templateVariables": {
                    "paymentType": "credit card",
                    "amount": "%s",
                    "currency": "INR",
                    "transactionDate": "2026-08-13",
                    "reference": "PAY-10045"
                  }
                }
                """.formatted(amount);
    }
}
