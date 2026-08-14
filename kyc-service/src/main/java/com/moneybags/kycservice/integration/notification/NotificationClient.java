package com.moneybags.kycservice.integration.notification;
import com.moneybags.kycservice.enums.KycStatus;
import com.moneybags.kycservice.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${services.notification.base-url}")
            String notificationBaseUrl
    ) {

        this.restClient = restClientBuilder
                .baseUrl(notificationBaseUrl)
                .build();
    }

    public void sendKycStatusNotification(
            Long cifId,
            KycStatus kycStatus,
            String rejectionReason
    ) {

        NotificationRequest request =
                new NotificationRequest(
                        cifId,
                        kycStatus,
                        rejectionReason
                );

        try {

            restClient.post()
                    .uri("/internal/v1/notifications/kyc-status")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception exception) {

            throw new ExternalServiceException(
                    "Failed to send KYC notification for cifId: "
                            + cifId,
                    exception
            );
        }
    }
}
