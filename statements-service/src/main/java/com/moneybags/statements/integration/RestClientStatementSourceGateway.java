package com.moneybags.statements.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "moneybags.statements", name = "stub-upstream-clients", havingValue = "false", matchIfMissing = true)
public class RestClientStatementSourceGateway implements StatementSourceGateway {
    private final RestClient accounting;
    private final RestClient deposit;
    public RestClientStatementSourceGateway(RestClient.Builder builder,
            @Value("${moneybags.clients.accounting.base-url}") String accountingUrl,
            @Value("${moneybags.clients.deposit.base-url}") String depositUrl) {
        accounting = builder.clone().baseUrl(accountingUrl).build(); deposit = builder.clone().baseUrl(depositUrl).build();
    }
    public StatementSource load(String accountReference, LocalDate start, LocalDate end) {
        List<LedgerEntry> ledger = new ArrayList<>();
        for (int page = 0, pages = 1; page < pages; page++) {
            int requestedPage = page;
            LedgerPage value = accounting.get().uri(uri -> uri.path("/internal/v1/ledger-entries").queryParam("accountReference", accountReference).queryParam("from", start).queryParam("to", end).queryParam("page", requestedPage).queryParam("size", 500).build()).retrieve().body(LedgerPage.class);
            if (value == null) break; ledger.addAll(value.content == null ? List.of() : value.content); pages = value.totalPages;
        }
        List<DepositActivity> activities = new ArrayList<>();
        for (int page = 0, pages = 1; page < pages; page++) {
            int requestedPage = page;
            DepositPage value = deposit.get().uri(uri -> uri.path("/api/internal/deposit-accounts/{accountId}/statement-activities").queryParam("from", start).queryParam("to", end).queryParam("page", requestedPage).queryParam("size", 500).build(accountReference)).retrieve().body(DepositPage.class);
            if (value == null) break; activities.addAll(value.content == null ? List.of() : value.content); pages = value.totalPages;
        }
        return new StatementSource(ledger, activities);
    }
    public static class LedgerPage { public List<LedgerEntry> content; public int totalPages; }
    public static class DepositPage { public List<DepositActivity> content; public int totalPages; }
}
