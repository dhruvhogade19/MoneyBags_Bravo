package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.entity.Journal;
import com.moneybags.accounting.entity.JournalLine;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.JournalLineRepository;
import com.moneybags.accounting.repository.JournalRepository;
import com.moneybags.accounting.repository.AccountingPeriodRepository;
import com.moneybags.accounting.repository.FinancialReconciliationRunRepository;
import com.moneybags.accounting.domain.DomainTypes.ReconciliationStatus;
import com.moneybags.accounting.domain.DomainTypes.JournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;

@Service
public class QueryService {
    private final JournalRepository journals;
    private final JournalLineRepository lines;
    private final JournalMapper mapper;
    private final LedgerBalanceService balances;
    private final AccountingPeriodRepository periods;
    private final FinancialReconciliationRunRepository reconciliations;

    public QueryService(JournalRepository journals, JournalLineRepository lines, JournalMapper mapper,
                        LedgerBalanceService balances, AccountingPeriodRepository periods,
                        FinancialReconciliationRunRepository reconciliations) {
        this.journals = journals; this.lines = lines; this.mapper = mapper; this.balances = balances;
        this.periods = periods; this.reconciliations = reconciliations;
    }

    @Transactional(readOnly = true)
    public JournalResponse journal(String number) {
        return mapper.toResponse(journals.findByJournalNumber(number).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "JOURNAL_NOT_FOUND", "Journal not found: " + number)), false);
    }

    @Transactional(readOnly = true)
    public JournalPage search(String journalNumber, LocalDate businessDate, String sourceService, String eventType,
                              String externalReference, JournalStatus status, int page, int size) {
        Page<Journal> result = journals.search(blankToNull(journalNumber), businessDate, blankToNull(sourceService),
                blankToNull(eventType), blankToNull(externalReference), status,
                PageRequest.of(page, size, Sort.by("postedAt").descending()));
        return new JournalPage(result.map(value -> mapper.toResponse(value, false)).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse balance(String reference) { return balances.accountBalance(reference); }

    @Transactional(readOnly = true)
    public LedgerEntryPage ledger(String reference, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && to.isBefore(from)) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_DATE_RANGE", "to must not be before from");
        Page<JournalLine> result = lines.findPageForAccount(reference, from, to,
                PageRequest.of(page, size, Sort.by("journal.postedAt").ascending().and(Sort.by("lineNumber"))));
        return new LedgerEntryPage(result.map(this::entry).getContent(), page, size, result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public LedgerEntryPage glPostings(String glCode, LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && to.isBefore(from)) throw new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_DATE_RANGE", "to must not be before from");
        Page<JournalLine> result = lines.findPageForGl(glCode, from, to,
                PageRequest.of(page, size, Sort.by("journal.postedAt").descending().and(Sort.by("lineNumber"))));
        return new LedgerEntryPage(result.map(this::entry).getContent(), page, size, result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AccountingDashboardResponse dashboard(LocalDate businessDate) {
        LocalDate date = businessDate == null ? LocalDate.now() : businessDate;
        var totalRows = journals.totalsForDate(date);
        Object[] totals = totalRows.isEmpty() ? new Object[0] : totalRows.getFirst();
        BigDecimal debit = totals.length == 0 || totals[0] == null ? BigDecimal.ZERO : (BigDecimal) totals[0];
        BigDecimal credit = totals.length < 2 || totals[1] == null ? BigDecimal.ZERO : (BigDecimal) totals[1];
        Page<Journal> recent = journals.search(null, date, null, null, null, null,
                PageRequest.of(0, 8, Sort.by("postedAt").descending()));
        return new AccountingDashboardResponse(date, journals.countByBusinessDate(date), debit, credit,
                journals.countUnbalanced(date), 0,
                periods.findByBusinessDate(date).map(value -> value.getStatus()).orElse(null),
                reconciliations.countByBusinessDateAndStatusAndActiveTrue(date, ReconciliationStatus.EXCEPTION),
                recent.map(value -> mapper.toResponse(value, false)).getContent());
    }

    private LedgerEntryResponse entry(JournalLine line) {
        Journal journal = line.getJournal();
        return new LedgerEntryResponse(journal.getJournalNumber(), line.getLineNumber(), journal.getBusinessDate(),
                journal.getOccurredAt(), journal.getEventType(), line.getGlCode(), line.getSubledgerReference(),
                line.getComponentType(), line.getDebitAmount(), line.getCreditAmount(), journal.getCurrencyCode(),
                line.getNarration());
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
}
