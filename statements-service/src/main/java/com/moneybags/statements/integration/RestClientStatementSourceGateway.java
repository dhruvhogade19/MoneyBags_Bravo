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

    public RestClientStatementSourceGateway(RestClient.Builder builder,
            @Value("${moneybags.clients.accounting.base-url}") String accountingUrl,
            @Value("${moneybags.clients.deposit.base-url}") String depositUrl) {
        accounting = builder.clone().baseUrl(accountingUrl).build();
        deposit = builder.clone().baseUrl(depositUrl).build();
    }

    @Override
    public AccountContext context(String accountReference) {
        AccountContext value = deposit.get()
                .uri("/api/internal/deposit-accounts/{accountId}/statement-context", accountReference)
                .retrieve().body(AccountContext.class);
        if (value == null) throw new IllegalStateException("Deposit returned no statement account context");
        return value;
    }

    @Override
    public StatementSource load(String accountReference, LocalDate start, LocalDate end) {
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
}
