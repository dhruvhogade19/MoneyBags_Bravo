package com.moneybags.kycservice.integration.notification;

import com.moneybags.kycservice.enums.KycStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationClientTests {

    private MockRestServiceServer server;
    private NotificationClient notificationClient;

    @Test
    void usesEurekaAwareRestClientBuilder() throws NoSuchMethodException {
        var constructor = NotificationClient.class.getConstructor(
                RestClient.Builder.class,
                String.class
        );

        assertTrue(constructor.getParameters()[0]
                .isAnnotationPresent(LoadBalanced.class));
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        notificationClient = new NotificationClient(
                builder,
                "http://notification-service"
        );
    }

    @Test
    void sendsApprovedStatusWithoutRejectionReason() {
        server.expect(once(), requestTo(
                        "http://notification-service/internal/v1/notifications/kyc-status"
                ))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "cifId": 101,
                          "kycStatus": "APPROVED"
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "notificationId": 501,
                          "cifId": 101,
                          "status": "SENT"
                        }
                        """, MediaType.APPLICATION_JSON));

        notificationClient.sendKycStatusNotification(
                101L,
                KycStatus.APPROVED,
                null
        );

        server.verify();
    }

    @Test
    void sendsRejectedStatusWithRejectionReason() {
        server.expect(once(), requestTo(
                        "http://notification-service/internal/v1/notifications/kyc-status"
                ))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "cifId": 101,
                          "kycStatus": "REJECTED",
                          "rejectionReason": "PAN document could not be verified."
                        }
                        """, true))
                .andRespond(withSuccess("""
                        {
                          "notificationId": 502,
                          "cifId": 101,
                          "status": "SENT"
                        }
                        """, MediaType.APPLICATION_JSON));

        notificationClient.sendKycStatusNotification(
                101L,
                KycStatus.REJECTED,
                "PAN document could not be verified."
        );

        server.verify();
    }
}
