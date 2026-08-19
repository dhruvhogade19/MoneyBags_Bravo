package com.moneybags.deposit.service;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.domain.DomainTypes.RecordStatus;
import com.moneybags.deposit.dto.StatementResponses.StatementAccountContext;
import com.moneybags.deposit.dto.StatementResponses.StatementActivity;
import com.moneybags.deposit.dto.StatementResponses.StatementActivityPage;
import com.moneybags.deposit.entity.DepositAccount;
import com.moneybags.deposit.exception.ApiException;
import com.moneybags.deposit.repository.DepositAccountRepository;
import com.moneybags.deposit.repository.DepositAccountTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class StatementActivityService {
    private static final List<DepositTransactionType> POSTED_TYPES =
            List.of(DepositTransactionType.DEBIT, DepositTransactionType.CREDIT);

    private final DepositAccountRepository accounts;
    private final DepositAccountTransactionRepository transactions;

    public StatementActivityService(DepositAccountRepository accounts,
                                    DepositAccountTransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public StatementAccountContext context(String accountId) {
        DepositAccount account = account(accountId);
        List<String> customerIds = account.getHolders().stream()
                .filter(holder -> holder.getStatus() == RecordStatus.ACTIVE)
                .map(holder -> holder.getCustomerId())
                .distinct()
                .toList();
        return new StatementAccountContext(account.getId(), mask(account.getAccountNumber()),
                account.getProductSubtype().name(), account.getCurrencyCode().trim(), customerIds);
    }

    @Transactional(readOnly = true)
    public StatementActivityPage activities(String accountId, LocalDate from, LocalDate to,
                                            int page, int size) {
        DepositAccount account = account(accountId);
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "to must not be before from");
        }
        var fromInstant = from.atStartOfDay().atOffset(ZoneOffset.UTC);
        var toExclusive = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        var result = transactions
                .findByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        accountId, POSTED_TYPES, fromInstant, toExclusive,
                        PageRequest.of(page, size, Sort.by("createdAt").ascending()));
        List<StatementActivity> content = result.getContent().stream()
                .map(value -> new StatementActivity(value.getId(), value.getPaymentId(),
                        value.getTransactionType().name(), value.getAmount(),
                        value.getCurrencyCode().trim(), value.getBalanceBefore(),
                        value.getBalanceAfter(), value.getCreatedAt()))
                .toList();
        var current = account.getBalance().getLedgerBalance();
        var opening = transactions
                .findFirstByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        accountId, POSTED_TYPES, fromInstant)
                .map(value -> value.getBalanceBefore()).orElse(current);
        var closing = transactions
                .findFirstByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        accountId, POSTED_TYPES, toExclusive)
                .map(value -> value.getBalanceBefore()).orElse(current);
        return new StatementActivityPage(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), opening, closing,
                account.getCurrencyCode().trim());
    }

    private DepositAccount account(String id) {
        return accounts.findDetailedById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account was not found"));
    }

    private String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) return accountNumber;
        return "X".repeat(accountNumber.length() - 4) + accountNumber.substring(accountNumber.length() - 4);
    }
}
