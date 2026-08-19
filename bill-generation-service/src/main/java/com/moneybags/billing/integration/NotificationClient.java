package com.moneybags.billing.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import com.moneybags.billing.config.ClientCredentialsTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Component
public class NotificationClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private static final DateTimeFormatter BILLING_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);

    private final RestClient restClient;
    private final boolean stub;

    @Autowired
    public NotificationClient(@Qualifier("billingNotificationRestClient") ObjectProvider<RestClient> restClient,
                              @Value("${moneybags.billing.stub-notification-client:false}") boolean stub,
                              ObjectProvider<ClientCredentialsTokenProvider> ignored) {
        this(stub ? null : restClient.getObject(), stub);
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

        try {
            restClient.post()
                    .uri("/internal/v1/notifications")
                    .header("Idempotency-Key", "bill-" + billId + "-generated")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            // The bill and its financial postings are authoritative. Email delivery is a
            // secondary side effect and must not roll back an otherwise valid bill.
            log.warn("Bill {} was generated, but its notification could not be delivered", billId, exception);
        }
    }

    record NotificationRequest(long cifId, String notificationType, String sourceReference,
                               Map<String, String> templateVariables) {
    }
}
