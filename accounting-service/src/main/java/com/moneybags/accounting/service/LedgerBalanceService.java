package com.moneybags.accounting.service;

import com.moneybags.accounting.api.AccountingDtos.*;
import com.moneybags.accounting.domain.DomainTypes.AccountType;
import com.moneybags.accounting.domain.DomainTypes.NormalBalance;
import com.moneybags.accounting.entity.GlAccount;
import com.moneybags.accounting.entity.JournalLine;
import com.moneybags.accounting.exception.ApiException;
import com.moneybags.accounting.repository.GlAccountRepository;
import com.moneybags.accounting.repository.JournalLineRepository;
import com.moneybags.accounting.repository.JournalRepository;
import com.moneybags.accounting.repository.SubledgerAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class LedgerBalanceService {
    private final JournalLineRepository lines;
    private final GlAccountRepository glAccounts;
    private final SubledgerAccountRepository subledgers;
    private final JournalRepository journals;

    public LedgerBalanceService(JournalLineRepository lines, GlAccountRepository glAccounts,
                                SubledgerAccountRepository subledgers, JournalRepository journals) {
        this.lines = lines; this.glAccounts = glAccounts; this.subledgers = subledgers; this.journals = journals;
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse accountBalance(String reference) {
        List<JournalLine> accountLines = lines.findAllForAccount(reference);
        String currency = subledgers.findByAccountReference(reference).stream().findFirst()
                .map(value -> value.getCurrencyCode().trim()).orElseGet(() -> accountLines.stream().findFirst()
                        .map(value -> value.getJournal().getCurrencyCode().trim()).orElse("INR"));
        BigDecimal total = accountLines.stream()
                .filter(line -> line.getJournal().getCurrencyCode().trim().equals(currency))
                .map(this::normalBalanceAmount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4);
        return new AccountBalanceResponse(reference, total, currency, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public AccountClearanceResponse clearance(AccountType type, String reference, String requestedCurrency) {
        var account = subledgers.findByAccountTypeAndAccountReference(type, reference)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_REGISTERED",
                        "The account is not registered in Accounting"));
        String currency = requestedCurrency == null || requestedCurrency.isBlank()
                ? account.getCurrencyCode().trim() : requestedCurrency;
        Map<String, BigDecimal> byRole = new TreeMap<>();
        long lastSequence = 0;
        for (JournalLine line : lines.findAllForAccount(reference)) {
            if (!line.getJournal().getCurrencyCode().trim().equals(currency)) continue;
            byRole.merge(line.getGlCode(), normalBalanceAmount(line), BigDecimal::add);
            lastSequence = Math.max(lastSequence, line.getJournal().getPostingSequence());
        }
        List<ClearanceBalance> balances = byRole.entrySet().stream()
                .map(entry -> new ClearanceBalance(currency, entry.getKey(), entry.getValue().setScale(4))).toList();
        boolean nonZero = balances.stream().anyMatch(value -> value.amount().compareTo(BigDecimal.ZERO) != 0);
        List<String> blockers = nonZero ? List.of("NON_ZERO_BALANCE") : List.of();
        return new AccountClearanceResponse(type, reference, !nonZero, balances, blockers, lastSequence,
                OffsetDateTime.now());
    }

    private BigDecimal normalBalanceAmount(JournalLine line) {
        GlAccount gl = glAccounts.findByGlCode(line.getGlCode()).orElseThrow(() -> new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR, "GL_ACCOUNT_NOT_FOUND", "Journal references an unknown GL account"));
        return gl.getNormalBalance() == NormalBalance.DEBIT
                ? line.getDebitAmount().subtract(line.getCreditAmount())
                : line.getCreditAmount().subtract(line.getDebitAmount());
    }
}
