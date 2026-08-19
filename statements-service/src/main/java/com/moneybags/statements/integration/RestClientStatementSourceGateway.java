package com.moneybags.statements.integration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "moneybags.statements", name = "stub-upstream-clients",
        havingValue = "false", matchIfMissing = true)
public class RestClientStatementSourceGateway implements StatementSourceGateway {
    private final RestClient accounting;
    private final RestClient deposit;
    private final RestClient creditCard;

    public RestClientStatementSourceGateway(RestClient.Builder builder,
            @Value("${moneybags.clients.accounting.base-url}") String accountingUrl,
            @Value("${moneybags.clients.deposit.base-url}") String depositUrl,
            @Value("${moneybags.clients.credit-card.base-url}") String creditCardUrl) {
        accounting = builder.clone().baseUrl(accountingUrl).build();
        deposit = builder.clone().baseUrl(depositUrl).build();
        creditCard = builder.clone().baseUrl(creditCardUrl).build();
    }

    @Override
    public AccountContext context(String accountReference) {
        if (isCreditCard(accountReference)) return cardContext(accountReference);
        AccountContext value = deposit.get()
                .uri("/api/internal/deposit-accounts/{accountId}/statement-context", accountReference)
                .retrieve().body(AccountContext.class);
        if (value == null) throw new IllegalStateException("Deposit returned no statement account context");
        return value;
    }

    @Override
    public StatementSource load(String accountReference, LocalDate start, LocalDate end) {
        if (isCreditCard(accountReference)) return loadCardActivity(accountReference, start, end);
        List<LedgerEntry> ledger = loadLedger(accountReference, start, end);
        List<DepositActivity> activities = new ArrayList<>();
        DepositPage firstPage = null;
        for (int page = 0, pages = 1; page < pages; page++) {
            int requestedPage = page;
            DepositPage value = deposit.get().uri(uri -> uri
                    .path("/api/internal/deposit-accounts/{accountId}/statement-activities")
                    .queryParam("from", start).queryParam("to", end)
                    .queryParam("page", requestedPage).queryParam("size", 500)
                    .build(accountReference)).retrieve().body(DepositPage.class);
            if (value == null) break;
            if (firstPage == null) firstPage = value;
            activities.addAll(value.content == null ? List.of() : value.content);
            pages = value.totalPages;
        }
        if (firstPage == null) throw new IllegalStateException("Deposit returned no statement activity page");
        return new StatementSource(ledger, activities, firstPage.openingBalance,
                firstPage.closingBalance, firstPage.currency);
    }

    private AccountContext cardContext(String accountReference) {
        CardAccountContext value = creditCard.get().uri(
                "/internal/v1/credit-card-accounts/{accountId}/statement-context", cardId(accountReference))
                .retrieve().body(CardAccountContext.class);
        if (value == null) throw new IllegalStateException("Credit Card returned no statement account context");
        return new AccountContext(value.accountId, value.maskedAccountReference, value.accountType,
                value.currency, value.customerIds);
    }

    private StatementSource loadCardActivity(String accountReference, LocalDate start, LocalDate end) {
        CardStatementSource value = creditCard.get().uri(uri -> uri
                .path("/internal/v1/credit-card-accounts/{accountId}/statement-activity")
                .queryParam("from", start).queryParam("to", end).build(cardId(accountReference)))
                .retrieve().body(CardStatementSource.class);
        if (value == null) throw new IllegalStateException("Credit Card returned no statement activity");
        List<DepositActivity> activities = (value.activities == null ? List.<CardActivity>of() : value.activities)
                .stream().map(activity -> new DepositActivity(activity.transactionId, activity.paymentId,
                        activity.direction, activity.amount, activity.currency, null, null,
                        activity.occurredAt)).toList();
        return new StatementSource(List.of(), activities, value.openingBalance, value.closingBalance,
                value.currency);
    }

    private static boolean isCreditCard(String reference) { return reference != null && reference.matches("CC-\\d+"); }
    private static Long cardId(String reference) { return Long.valueOf(reference.substring(3)); }

    private List<LedgerEntry> loadLedger(String accountReference, LocalDate start, LocalDate end) {
        List<LedgerEntry> ledger = new ArrayList<>();
        try {
            for (int page = 0, pages = 1; page < pages; page++) {
                int requestedPage = page;
                LedgerPage value = accounting.get().uri(uri -> uri
                        .path("/internal/v1/ledger-entries")
                        .queryParam("accountReference", accountReference)
                        .queryParam("from", start).queryParam("to", end)
                        .queryParam("page", requestedPage).queryParam("size", 500).build())
                        .retrieve().body(LedgerPage.class);
                if (value == null) break;
                ledger.addAll(value.content == null ? List.of() : value.content);
                pages = value.totalPages;
            }
        } catch (RestClientException unavailable) {
            return List.of();
        }
        return ledger;
    }

    public static class LedgerPage { public List<LedgerEntry> content; public int totalPages; }
    public static class DepositPage {
        public List<DepositActivity> content;
        public int totalPages;
        public java.math.BigDecimal openingBalance;
        public java.math.BigDecimal closingBalance;
        public String currency;
    }
    public static class CardAccountContext {
        public String accountId; public String maskedAccountReference; public String accountType;
        public String currency; public List<String> customerIds;
    }
    public static class CardActivity {
        public String transactionId; public String paymentId; public String direction; public java.math.BigDecimal amount;
        public String currency; public java.time.OffsetDateTime occurredAt;
    }
    public static class CardStatementSource {
        public List<CardActivity> activities; public java.math.BigDecimal openingBalance;
        public java.math.BigDecimal closingBalance; public String currency;
    }
}
