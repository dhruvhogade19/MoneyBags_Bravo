package com.moneybags.deposit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.moneybags.deposit.domain.DomainTypes.DepositTransactionType;
import com.moneybags.deposit.domain.DomainTypes.PaymentOperationType;
import com.moneybags.deposit.entity.AccountBalance;
import com.moneybags.deposit.entity.DepositAccount;
import com.moneybags.deposit.entity.DepositAccountTransaction;
import com.moneybags.deposit.repository.DepositAccountRepository;
import com.moneybags.deposit.repository.DepositAccountTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class StatementActivityServiceTest {
    @Test
    void returnsOnlyPostedActivityWithPeriodBalances() {
        DepositAccountRepository accounts = mock(DepositAccountRepository.class);
        DepositAccountTransactionRepository transactions = mock(DepositAccountTransactionRepository.class);
        DepositAccount account = mock(DepositAccount.class);
        AccountBalance balance = mock(AccountBalance.class);
        when(account.getBalance()).thenReturn(balance);
        when(account.getCurrencyCode()).thenReturn("INR");
        when(balance.getLedgerBalance()).thenReturn(new BigDecimal("800.0000"));
        when(accounts.findDetailedById("ACC-1")).thenReturn(Optional.of(account));
        OffsetDateTime occurredAt = OffsetDateTime.now().minusDays(1);
        DepositAccountTransaction transaction = new DepositAccountTransaction("TXN-1", "ACC-1",
                "PAY-1", "RES-1", DepositTransactionType.DEBIT,
                PaymentOperationType.BOOK_TRANSFER, new BigDecimal("200.0000"), "INR",
                new BigDecimal("1000.0000"), new BigDecimal("800.0000"), "CORR-1");
        when(transactions.findByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq("ACC-1"), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(transaction)));
        when(transactions.findFirstByAccountIdAndTransactionTypeInAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                eq("ACC-1"), any(), any())).thenReturn(Optional.of(transaction), Optional.empty());
        StatementActivityService service = new StatementActivityService(accounts, transactions);

        var result = service.activities("ACC-1", LocalDate.now().minusDays(7),
                LocalDate.now(), 0, 100);

        assertEquals(1, result.totalElements());
        assertEquals("DEBIT", result.content().getFirst().direction());
        assertEquals(new BigDecimal("1000.0000"), result.openingBalance());
        assertEquals(new BigDecimal("800.0000"), result.closingBalance());
    }
}
