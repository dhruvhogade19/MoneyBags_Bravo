package com.moneybags.accounting.controller;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.service.QueryService;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import com.moneybags.accounting.domain.DomainTypes.JournalStatus;

@RestController
@Validated
public class QueryController {
    private final QueryService queries;
    public QueryController(QueryService queries) { this.queries = queries; }

    @GetMapping("/api/v1/journals/{journalNumber}")
    JournalResponse journal(@PathVariable String journalNumber) { return queries.journal(journalNumber); }

    @GetMapping("/api/v1/journals")
    JournalPage journals(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                         LocalDate businessDate,
                         @RequestParam(required = false) String journalNumber,
                         @RequestParam(required = false) String sourceService,
                         @RequestParam(required = false) String eventType,
                         @RequestParam(required = false) String externalReference,
                         @RequestParam(required = false) JournalStatus status,
                         @RequestParam(defaultValue = "0") @Min(0) int page,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return queries.search(journalNumber, businessDate, sourceService, eventType, externalReference, status,
                page, size);
    }

    @GetMapping("/internal/v1/account-balances/{accountReference}")
    AccountBalanceResponse balance(@PathVariable String accountReference) { return queries.balance(accountReference); }

    @GetMapping("/api/v1/account-ledgers/{accountReference}/balance")
    AccountBalanceResponse publicBalance(@PathVariable String accountReference) {
        return queries.balance(accountReference);
    }

    @GetMapping("/internal/v1/ledger-entries")
    LedgerEntryPage ledger(@RequestParam @NotBlank String accountReference,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return queries.ledger(accountReference, from, to, page, size);
    }

    @GetMapping("/api/v1/account-ledgers/{accountReference}/entries")
    LedgerEntryPage publicLedger(@PathVariable String accountReference,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return queries.ledger(accountReference, from, to, page, size);
    }

    @GetMapping("/api/v1/gl-accounts/{glCode}/postings")
    LedgerEntryPage glPostings(@PathVariable String glCode,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "0") @Min(0) int page,
                           @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return queries.glPostings(glCode, from, to, page, size);
    }

    @GetMapping("/api/v1/accounting/dashboard")
    AccountingDashboardResponse dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return queries.dashboard(businessDate);
    }
}
