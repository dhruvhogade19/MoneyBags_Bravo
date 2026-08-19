package com.moneybags.billing.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class NotificationClientTest {

    @Test
    void sendsTheAgreedBillGeneratedNotificationContract() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationClient client = new NotificationClient(
                builder.baseUrl("http://localhost:8090").build(), false);

        server.expect(once(), requestTo("http://localhost:8090/internal/v1/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "bill-BILL-202608-001-generated"))
                .andExpect(content().json("""
                        {
                          "cifId": 101,
                          "notificationType": "BILL_GENERATED",
                          "sourceReference": "BILL-202608-001",
                          "templateVariables": {
                            "billId": "BILL-202608-001",
                            "billingPeriod": "01 Aug 2026 - 31 Aug 2026",
                            "currency": "INR",
                            "totalAmount": "8400.00",
                            "dueDate": "2026-09-15"
                          }
                        }
                        """))
                .andRespond(withSuccess());

        client.sendBillGenerated(101L, "BILL-202608-001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "INR",
                new BigDecimal("8400.0000"), LocalDate.of(2026, 9, 15));

        server.verify();
    }

    @Test
    void notificationFailureDoesNotFailAnAlreadyGeneratedBill() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NotificationClient client = new NotificationClient(
                builder.baseUrl("http://localhost:8090").build(), false);

        server.expect(once(), requestTo("http://localhost:8090/internal/v1/notifications"))
                .andExpect(content().json("""
                        {
                          "templateVariables": {
                            "billingPeriod": "01 Aug 2026 - 19 Aug 2026"
                          }
                        }
                        """))
                .andRespond(withServerError());

        client.sendBillGenerated(101L, "BILL-202608-002",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 19), "INR",
                new BigDecimal("8400.00"), LocalDate.of(2026, 9, 15));

        server.verify();
    }
}
