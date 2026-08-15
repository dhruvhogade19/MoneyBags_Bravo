package com.moneybags.billing.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationClient {
    private static final DateTimeFormatter BILLING_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final RestClient restClient;
    private final boolean stub;

    @Autowired
    public NotificationClient(@Value("${NOTIFICATION_URL:http://localhost:8090}") String notificationUrl,
                              @Value("${moneybags.billing.stub-notification-client:true}") boolean stub) {
        this(RestClient.builder().baseUrl(notificationUrl).build(), stub);
    }

    NotificationClient(RestClient restClient, boolean stub) {
        this.restClient = restClient;
        this.stub = stub;
    }

    public void sendBillGenerated(long cifId, String billId, String billingPeriod, String currency,
                                  BigDecimal totalAmount, LocalDate dueDate) {
        if (stub) {
            return;
        }

        YearMonth period = YearMonth.parse(billingPeriod);
        String displayPeriod = period.atDay(1).format(BILLING_DATE)
                + " - " + period.atEndOfMonth().format(BILLING_DATE);
        Map<String, String> templateVariables = Map.of(
                "billId", billId,
                "billingPeriod", displayPeriod,
                "currency", currency.trim(),
                "totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "dueDate", dueDate.toString());
        NotificationRequest request = new NotificationRequest(
                cifId, "BILL_GENERATED", billId, templateVariables);

        restClient.post()
                .uri("/internal/v1/notifications")
                .header("Idempotency-Key", "bill-" + billId + "-generated")
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    record NotificationRequest(long cifId, String notificationType, String sourceReference,
                               Map<String, String> templateVariables) {
    }
}
